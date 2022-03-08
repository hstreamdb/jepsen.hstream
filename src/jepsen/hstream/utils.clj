(ns jepsen.hstream.utils (:gen-class))

(defn in? "true if coll contains elm" [coll elm] (some #(= elm %) coll))

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

(defn is-hstream-client-exception?
  [e]
  (= (-> (Throwable->map e)
         :via
         second
         :type)
     (-> io.hstream.HStreamDBClientException
         .getName
         symbol)))

(defn is-hstream-client-unavailable-exception?
  [e]
  (and (is-hstream-client-exception? e)
       (= (-> (Throwable->map e)
              :via
              second
              :message)
          "io.grpc.StatusException: UNAVAILABLE: io exception")))
