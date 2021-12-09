(ns jepsen.hstream.utils
  (:gen-class))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
    (catch Exception e nil)))

(defn queue-property [pushes pops]
  (:is-valid
   (reduce (fn [acc x]
             (let [is-valid (:is-valid acc)
                   cur-ref  (:cur-ref  acc)]
               (if is-valid
                (if (some #{x} cur-ref)
                  (let [[lh rh] (split-with (complement #{x}) cur-ref)
                        new-ref  (rest rh)]
                    {:is-valid true
                     :cur-ref  new-ref})
                  {:is-valid false
                   :cur-ref cur-ref})
                acc))) {:is-valid true :cur-ref (into [] pushes)} (into [] pops))))
