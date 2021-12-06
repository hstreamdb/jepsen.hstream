(ns jepsen.hstream.checker
  "Validates that a history is correct with respect to some model."
  (:refer-clojure :exclude [set])
  (:require [clojure [core :as c]
                     [set :as set]
                     [stacktrace :as trace]
                     [string :as str]]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :refer [info warn]]
            [jepsen.util :as util]
            [knossos [op :as op]]
            [slingshot.slingshot :refer [try+ throw+]]
            [jepsen.checker :refer :all]
            [jepsen.hstream.utils :refer [queue-property]]))

(defn set+
  "Given a set of :add operations followed by **A SERIES OF** final :read, verifies that
  every successfully added element is present in the read, and that the read
  contains only elements for which an add was attempted."
  []
  (reify Checker
    (check [this test history opts]
      (let [read-stream (->> history
                              (r/filter op/ok?)
                              (r/filter #(= :read (:f %)))
                              (r/map :stream)
                              (reduce (fn [_ x] x) nil))
            attempts (->> history
                          (r/filter op/invoke?)
                          (r/filter #(= :add (:f %)))
                          (r/map :value)
                          (into []))
            adds (->> history
                      (r/filter op/ok?)
                      (r/filter #(= :add (:f %)))
                      (r/map :value)
                      (into []))
            this-adds (->> history
                           (r/filter op/ok?)
                           (r/filter #(= :add (:f %)))
                           (r/filter #(= read-stream (:stream %)))
                           (r/map :value)
                           (into []))
            reads (->> history
                       (r/filter op/ok?)
                       (r/filter #(= :read (:f %)))
                       (r/map :value)
                       (into []))
            final-read (->> reads
                            (reduce (fn [acc x] (set/union acc (into #{} x))) #{}))
            final-read-overlap (->> reads
                                    (reduce (fn [acc x]
                                              (if (empty? acc)
                                                (into #{} x)
                                                (set/intersection acc (into #{} x)))) #{}))
            ]
        (if-not final-read
          {:valid? :unknown
           :error  "Set was never read"}

          (let [all-add-fail (set/difference (into #{} attempts) (into #{} adds))

                ; The THIS-OK set is every read value which we tried to
                ; add to certain position
                this-ok     (set/intersection final-read (into #{} this-adds))

                ; Unexpected records are those we *never* attempted.
                unexpected  (set/difference final-read (into #{} attempts))

                ; This-Lost records are those we definitely added but weren't read
                this-lost   (set/difference (into #{} this-adds) final-read)

                reads-queue-property (map #(queue-property this-adds %) reads)]

            {:valid?                          (and (empty? this-lost)
                                                   (empty? unexpected)
                                                   (empty? all-add-fail)
                                                   (reduce #(and %1 %2) reads-queue-property))
             :All-Adds-ATTEMPED               (count attempts)
             :All-Adds-SUCCEEDED              (count adds)
             :All-Adds-FAILED                 (count all-add-fail)
             :All-Adds-UNEXPECTED             (count unexpected)
             :This-stream-Adds-SUCCEEDED      (count this-adds)
             :All-clients-Read-OVERLAP        (util/integer-interval-set-str final-read-overlap)
             :All-clients-Read-Order-Property reads-queue-property
             :This-stream-Read-OK             (count this-ok)
             :This-stream-Read-LOST           (count this-lost)
             :This-stream-Read-OK-details     (util/integer-interval-set-str this-ok)
             :This-stream-Read-LOST-details   (util/integer-interval-set-str this-lost)
             :This-stream-Read-details        (map #(into [] %) reads)
             :This-stream-Adds-SUCCEEDED-details this-adds
             :All-Adds-SUCCEEDED-details      (util/integer-interval-set-str (into #{} adds))
             :All-Adds-UNEXPECTED-details     (util/integer-interval-set-str unexpected)
             :All-Adds-FAILED-details         (util/integer-interval-set-str all-add-fail)}))))))
