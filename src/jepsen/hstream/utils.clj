(ns jepsen.hstream.utils
  (:gen-class)
  (:require [random-string.core :as rs]))

;;;; I. Clojure common helper functions
(defn in? "true if coll contains elm" [coll elm] (some #(= elm %) coll))

(defn insert [v i e] (vec (concat (subvec v 0 i) [e] (subvec v i))))

(defn indices [pred coll] (keep-indexed #(when (pred %2) %1) coll))

(defn first-index [pred coll] ((comp first indices) pred coll))

(defn is-sorted?
  "true if the coll is already sorted by `<=`"
  [coll]
  (apply <= coll))

(defn parse-int
  [number-string]
  (try (Integer/parseInt number-string) (catch Exception e nil)))

(defn map-value [f m] (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn queue-property
  [pushes pops]
  (:is-valid (reduce (fn [acc x]
                       (let [is-valid (:is-valid acc)
                             cur-ref (:cur-ref acc)]
                         (if is-valid
                           (if (some #{x} cur-ref)
                             (let [[lh rh] (split-with (complement #{x})
                                                       cur-ref)
                                   new-ref (rest rh)]
                               {:is-valid true, :cur-ref new-ref})
                             {:is-valid false, :cur-ref cur-ref})
                           acc)))
               {:is-valid true, :cur-ref (into [] pushes)}
               (into [] pops))))

;;;; II. HStream helper functions
(defn random-stream-name
  [length-except-prefix]
  (let [prefix "stream_"] (str prefix (rs/string length-except-prefix))))

(defn is-hserver-node? [node] (in? ["n1" "n2" "n3" "n4" "n5"] node))

(defn is-hstream-client-exception?
  [e]
  (= (-> (Throwable->map e)
         :via
         second
         :type)
     (-> io.hstream.HStreamDBClientException
         .getName
         symbol)))

(defn is-hstream-client-retriable-exception?
  [e]
  (and
    (is-hstream-client-exception? e)
    (in?
      ["io.grpc.StatusException: UNAVAILABLE: io exception"
       "io.grpc.StatusException: FAILED_PRECONDITION: Send appendRequest to wrong Server."]
      (-> (Throwable->map e)
          :via
          second
          :message))))
