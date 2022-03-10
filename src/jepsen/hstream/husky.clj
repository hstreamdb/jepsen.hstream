(ns jepsen.hstream.husky
  (:gen-class)
  (:require [jepsen.hstream.husky.utils :refer :all]
            [jepsen.hstream.utils :refer [insert first-index]]
            [jepsen.generator :as gen]
            [random-string.core :as rs]))

;; Parameters:
;; 1. max-streams
;; 2. max-keys
;; 3. max-write-number
;; 4. read-wait-time
;; 5. read-number
;; 6. freq

;; Generates a series of hstream operators with abundant randomness.
;; There are 4 types of operators:
;; [write  s_i] {:type :invoke :f :add  :value _        :stream s_i :key _}
;; [sub    s_i] {:type :invoke :f :sub                  :stream s_i}
;; [read   s_i] {:type :invoke :f :read :consumer-id _  :stream s_i}
;; [create s_i] {:type :invoke :f :create               :create :stream s_i}

;; There are some restrictions:
;; 1. There is **exactly 1** [create s_i] operator before any [write s_i], [sub
;; s_i] or [read s_i].
;; 2. There is **exactly 1** [sub s_i] operator before any [read s_i].
;; 3. There is **no** [write s_i] (read-wait-time/freq) seconds after a [read
;; s_i].

(def sample-paras
  {:max-streams 10,
   :max-write-number 100,
   :max-read-number 10,
   :read-wait-time 120})

(defn husky-gen-write
  [stream value]
  {:type :invoke, :f :add, :value value, :stream stream})

(defn husky-gen-read
  [stream id]
  {:type :invoke, :f :read, :value nil, :consumer-id id, :stream stream})

(defn husky-gen-sub
  [stream]
  {:type :invoke, :f :sub, :value nil, :stream stream})

(defn husky-gen-create
  [stream]
  {:type :invoke, :f :create, :value nil, :stream stream})


(defn seq-to-generators
  [coll paras]
  (let [read-wait-time (:read-wait-time paras)]
    (map #(case (:f %)
            :add (gen/once %)
            :read (gen/phases (gen/once %) (gen/sleep read-wait-time))
            :sub (gen/once %)
            :create (gen/once %))
      coll)))

;; Generate!
(defn husky-generate
  [paras]
  (let [;; Basic parameters
        max-streams (:max-streams paras)
        max-write-number (:max-write-number paras)
        max-read-number (:max-read-number paras)
        ;; Randomly generated streams
        streams (repeatedly max-streams #(rs/string 10))
        read-streams (repeatedly max-read-number #(rand-nth streams))
        ;; Start...
        each-stream-write-number (split-integer-2 max-write-number max-streams)
        sorted-writes
          (apply concat
            (map (fn [index]
                   (map (fn [_] (husky-gen-write (nth streams index) index))
                     (range (nth each-stream-write-number index))))
              (range max-streams)))
        sorted-reads (map (fn [index]
                            (husky-gen-read (nth read-streams index) index))
                       (range max-read-number))
        shuffled-writes-with-reads
          (shuffle (into [] (concat sorted-writes sorted-reads)))
        earliest-read-of-each-stream
          (reduce (fn [acc stream]
                    (let [index (first-index (fn [item]
                                               (= (:stream item) stream))
                                             shuffled-writes-with-reads)]
                      (assoc acc stream index)))
            {}
            streams)
        earliest-read-of-each-stream-amended
          (into {}
                (map (fn [[stream index]]
                       (if (nil? index)
                         {stream (+ max-write-number max-read-number)}
                         {stream index}))
                  earliest-read-of-each-stream))
        sub-inserted
          (reduce (fn [acc stream]
                    (let [pos (get earliest-read-of-each-stream-amended stream)
                          val (husky-gen-sub stream)]
                      (insert acc pos val)))
            shuffled-writes-with-reads
            (distinct read-streams))
        earliest-rws-of-each-stream
          (reduce (fn [acc stream]
                    (let [index (first-index (fn [item]
                                               (= (:stream item) stream))
                                             sub-inserted)]
                      (assoc acc stream index)))
            {}
            streams)
        create-inserted (reduce (fn [acc stream]
                                  (let [pos (get earliest-rws-of-each-stream
                                                 stream)
                                        val (husky-gen-create stream)]
                                    (insert acc pos val)))
                          sub-inserted
                          streams)]
    (gen/phases (seq-to-generators create-inserted paras))))
