(ns jepsen.hstream.utils
  (:gen-class))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
    (catch Exception e nil)))
