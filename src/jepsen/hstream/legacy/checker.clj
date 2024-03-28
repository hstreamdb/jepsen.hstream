(ns jepsen.hstream.legacy.checker
  "Validates that a history is correct with respect to some model."
  (:refer-clojure :exclude [set])
  (:require
    [clojure [core :as c] [set :as set] [stacktrace :as trace] [string :as str]]
    [clojure.core.reducers :as r]
    [clojure.tools.logging :refer [info warn]]
    [jepsen.util :as util]
    [knossos [op :as op]]
    [slingshot.slingshot :refer [try+ throw+]]
    [jepsen.checker :refer :all]
    [jepsen.hstream.common.utils :refer [queue-property is-sorted? map-value]]))

(defn set+
  "Given a set of :add operations followed by **A SERIES OF** final :read, verifies that
  every successfully added element is present in the read, and that the read
  contains only elements for which an add was invoked."
  []
  (reify
    Checker
      (check [this test history opts]
        (let [;; #{stream}
              read-streams (->> history
                                (r/filter op/ok?)
                                (r/filter #(= :read (:f %)))
                                (r/map :stream)
                                (into #{}))
              ;; {stream [value]}
              add-invokes (->> history
                            (r/filter op/invoke?)
                            (r/filter #(= :add (:f %)))
                            (group-by :stream)
                            (map-value #(into [] (map :value %))))
              ;; {stream [value]}
              add-oks (->> history
                        (r/filter op/ok?)
                        (r/filter #(= :add (:f %)))
                        (group-by :stream)
                        (map-value #(into [] (map :value %))))
              add-infos (->> history
                          (r/filter op/info?)
                          (r/filter #(= :add (:f %)))
                          (group-by :stream)
                          (map-value #(into [] (map :value %))))
              add-fails (->> history
                          (r/filter op/fail?)
                          (r/filter #(= :add (:f %)))
                          (group-by :stream)
                          (map-value #(into [] (map :value %))))
              ;; {stream [value]}
              add-total-retries (->> history
                                     (r/filter #(or (op/ok? %) (op/fail? %)))
                                     (r/filter #(= :add (:f %)))
                                     (r/filter #(= true (:retry? %)))
                                     (group-by :stream)
                                     (map-value #(into [] (map :value %))))
              ;; {stream [value]}
              add-succeeded-retries (->> history
                                         (r/filter op/ok?)
                                         (r/filter #(= :add (:f %)))
                                         (r/filter #(= true (:retry? %)))
                                         (group-by :stream)
                                         (map-value #(into [] (map :value %))))
              ;; {stream [value]}
              add-failed-retries (->> history
                                      (r/filter op/fail?)
                                      (r/filter #(= :add (:f %)))
                                      (r/filter #(= true (:retry? %)))
                                      (group-by :stream)
                                      (map-value #(into [] (map :value %))))
              ;; {stream [[value]]}
              reads-raw (->> history
                             (r/filter op/ok?)
                             (r/filter #(= :read (:f %)))
                             (group-by :stream)
                             (map-value #(into [] (map :value %))))
              ;; {stream [value]}
              reads (->> reads-raw
                         (map-value #(into [] (apply concat %))))
              ;; {stream #{value}}
              reads-overlap
                (map-value
                  #(:intersection
                     (reduce (fn [acc x]
                               (let [union (:union acc)
                                     intersection (:intersection acc)
                                     new-intersection
                                       (set/intersection union (into #{} x))
                                     new-union (set/union union (into #{} x))]
                                 {:union new-union,
                                  :intersection (set/union intersection
                                                           new-intersection)}))
                       {:union #{}, :intersection #{}}
                       %))
                  reads-raw)]
          (if-not reads
            {:valid? :unknown, :error "Set was never read"}
            (let [;; {stream #{value}}
                  add-fail (into {} add-fails)
                  add-info (into {} add-infos)
                  ;; {stream #{value}}
                  read-correct (into {}
                                     (map (fn [[k1 v1]]
                                            (let [v2 (get add-oks k1)]
                                              [k1
                                               (set/intersection (into #{} v1)
                                                                 (into #{}
                                                                       v2))]))
                                       reads))
                  ;; {stream #{value}}
                  read-lost (into {}
                                  (map (fn [[k1 v1]]
                                         (let [v2 (get add-oks k1)]
                                           [k1
                                            (set/difference (into #{} v2)
                                                            (into #{} v1))]))
                                    reads))
                  ;; {stream #{value}}
                  read-unexpected (into {}
                                        (map (fn [[k1 v1]]
                                               (let [v2 (get add-invokes k1)]
                                                 [k1
                                                  (set/difference (into #{} v1)
                                                                  (into #{}
                                                                        v2))]))
                                          reads))
                  ; The basic requirement is that the order of messages read
                  ; from DB
                  ; follows the one when the `add` operations were **DONE**.
                  ; This only
                  ; applies to the SYNC write mode.
                  ; Extraly, we require that the order of messages read from DB
                  ; follows the one when the `add` operations were **CALLED**.
                  ; This is
                  ; what 'order property' really means.
                  ; NOTE THE SECOND PROPERTY HOLDS ONLY WHEN USING **PURE** SYNC
                  ; WRITING
                  ; MODE. HOWEVER, THE CURRENT SYNC MODE DOES NOT HOLD THE
                  ; PROPERTY.
                  ;; {stream bool}
                  read-queue-property
                    (into {}
                          (map (fn [[k1 v1s]]
                                 (let [v2 (get add-oks k1)
                                       this-stream-every-consumer-properties
                                         (map #(queue-property v2 %) v1s)]
                                   [k1
                                    (reduce #(and %1 %2)
                                      this-stream-every-consumer-properties)]))
                            reads-raw))]
              {;; ----------------- VALID -----------------
               :valid? (reduce #(and %1 %2)
                         (concat (vals (map-value empty? read-lost))
                                 (vals (map-value empty? read-unexpected))
                                 (vals (map-value empty? add-fail))
                                 ; (vals read-queue-property)
                         )),
               ;; ----------------- verbose-0, total -----------------
               :verbose-0
                 {:Adds {:Adds-INVOKED-total (->> (map-value count add-invokes)
                                                  vals
                                                  (reduce +)),
                         :Adds-SUCCEEDED-total (->> (map-value count add-oks)
                                                    vals
                                                    (reduce +)),
                         :Adds-FAILED-total (->> (map-value count add-fail)
                                                 vals
                                                 (reduce +)),
                         :Adds-INFO-total (->> (map-value count add-info)
                                               vals
                                               (reduce +)),
                         :Adds-RETRIED-total (->> (map-value count
                                                             add-total-retries)
                                                  vals
                                                  (reduce +)),
                         :Adds-RETRIED-SUCCEEDED-total
                           (->> (map-value count add-succeeded-retries)
                                vals
                                (reduce +)),
                         :Adds-RETRIED-FAILED-total
                           (->> (map-value count add-failed-retries)
                                vals
                                (reduce +))},
                  :Reads {:Reads-STREAMS read-streams,
                          :Reads-FETCHED-total (->> (map-value count reads)
                                                    vals
                                                    (reduce +)),
                          :Reads-CORRECT-total (->> (map-value count
                                                               read-correct)
                                                    vals
                                                    (reduce +)),
                          :Reads-LOST-total (->> (map-value count read-lost)
                                                 vals
                                                 (reduce +)),
                          :Reads-UNEXPECTED-total
                            (->> (map-value count read-unexpected)
                                 vals
                                 (reduce +))}},
               ;; ----------------- verbose-1, each stream -----------------
               :verbose-1
                 {:Adds {:Adds-INVOKED-count (map-value count add-invokes),
                         :Adds-SUCCEEDED-count (map-value count add-oks),
                         :Adds-FAILED-count (map-value count add-fail),
                         :Adds-INFO-count (map-value count add-info),
                         :Adds-RETRIED-count (map-value count
                                                        add-total-retries),
                         :Adds-RETRIED-SUCCEEDED-count
                           (map-value count add-succeeded-retries),
                         :Adds-RETRIED-FAILED-count
                           (map-value count add-failed-retries)},
                  :Reads {:Reads-STREAMS read-streams,
                          :Reads-FETCHED-count (map-value count reads),
                          :Reads-CORRECT-count (map-value count read-correct),
                          :Reads-LOST-count (map-value count read-lost),
                          :Reads-UNEXPECTED-count (map-value count
                                                             read-unexpected)}},
               ;; ----------------- verbose-2, details -----------------
               :verbose-2
                 {:Adds {:Adds-INVOKED-details (map str add-invokes),
                         :Adds-SUCCEEDED-details (map str add-oks),
                         :Adds-FAILED-details (map str add-fail),
                         :Adds-INFO-details (map str add-info),
                         :Adds-RETRIED-details (map str add-total-retries),
                         :Adds-RETRIED-SUCCEEDED-details
                           (map str add-succeeded-retries),
                         :Adds-RETRIED-FAILED-details (map str
                                                        add-failed-retries)},
                  :Reads {:Reads-STREAMS read-streams,
                          :Reads-FETCHED-details (map-value str reads-raw),
                          :Reads-CORRECT-details (map-value str read-correct),
                          :Reads-LOST-details (map-value str read-lost),
                          :Reads-UNEXPECTED-details (map-value str
                                                               read-unexpected),
                          :Reads-ORDER-PROPERTY        read-queue-property
                          :Reads-OVERLAP (map-value str reads-overlap)}}}))))))
