(ns jepsen.hstream.husky.utils
  (:gen-class)
  (:require [clojure.core.reducers :as reducers]))

(defn rand-int-exclude-zero
  [n]
  (if (= n 1)
    1
    (let [result (rand-int n)]
      (if (zero? result) (rand-int-exclude-zero n) result))))

(defn split-integer
  [number parts]
  (let [reducef
          (fn [acc x]
            (let [acc-sum (reduce + acc)
                  amend (- parts (+ x 1))]
              (conj acc (rand-int-exclude-zero (- number (+ acc-sum amend))))))]
    (reduce reducef [] (range parts))))

(defn split-integer-2
  [number parts]
  (let [avg (/ number parts)
        init-seq (into []
                       (repeatedly parts
                                   #(int (Math/floor (+ (/ avg 2)
                                                        (rand-int-exclude-zero
                                                          avg))))))
        init-sum (reduce + init-seq)
        delta (- number init-sum)
        each-delta (quot delta parts)
        last-delta (rem delta parts)
        altered-seq (into [] (map #(+ % each-delta) init-seq))
        first-item (first altered-seq)]
    (assoc altered-seq 0 (+ first-item last-delta))))
