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
            [jepsen.checker :refer :all]))

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
                          (into #{}))
            adds (->> history
                      (r/filter op/ok?)
                      (r/filter #(= :add (:f %)))
                      (r/map :value)
                      (into #{}))
            this-adds (->> history
                           (r/filter op/ok?)
                           (r/filter #(= :add (:f %)))
                           (r/filter #(= read-stream (:stream %)))
                           (r/map :value)
                           (into #{}))
            final-read (->> history
                            (r/filter op/ok?)
                            (r/filter #(= :read (:f %)))
                            (r/map :value)
                            (reduce (fn [acc x] (set/union acc (into #{} x))) #{}))
            final-read-overlap (->> history
                                    (r/filter op/ok?)
                                    (r/filter #(= :read (:f %)))
                                    (r/map :value)
                                    (reduce (fn [acc x]
                                              (if (empty? acc)
                                                (into #{} x)
                                                (set/intersection acc (into #{} x)))) #{}))
            ]
        (if-not final-read
          {:valid? :unknown
           :error  "Set was never read"}

          (let [; The THIS-OK set is every read value which we tried to
                ; add to certain position
                this-ok     (set/intersection final-read this-adds)

                ; Unexpected records are those we *never* attempted.
                unexpected  (set/difference final-read attempts)

                ; This-Lost records are those we definitely added but weren't read
                this-lost   (set/difference this-adds final-read)

                ; This-Recovered records are those where we didn't know if the add
                ; succeeded or not, but we found them in the final set.
                this-recovered (set/difference this-ok this-adds)]

            {:valid?                      (and (empty? this-lost)
                                               (empty? unexpected)
                                               (empty? final-read-overlap))
             :all-attempt-count           (count attempts)
             :all-acknowledged-count      (count adds)
             :all-unexpected-count        (count unexpected)
             :clients-read-overlap        (util/integer-interval-set-str final-read-overlap)
             :this-stream-ok-count        (count this-ok)
             :this-stream-lost-count      (count this-lost)
             :this-stream-recovered-count (count this-recovered)
             :this-stream-ok              (util/integer-interval-set-str this-ok)
             :this-stream-lost            (util/integer-interval-set-str this-lost)
             :all-unexpected              (util/integer-interval-set-str unexpected)
             :source-recovered            (util/integer-interval-set-str this-recovered)}))))))
