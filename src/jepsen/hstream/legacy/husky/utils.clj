(ns jepsen.hstream.legacy.husky.utils
  (:require [clojure.core.reducers :as reducers]
            [jepsen.hstream.common.utils :refer [first-index]]
            [jepsen.generator :as gen]))

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

;;;;

(defn is-sub-gen? [gen] (= (:f gen) :sub))

(defn is-create-gen? [gen] (= (:f gen) :create))

(defn is-phases-gen? [gen] (or (is-create-gen? gen) (is-sub-gen? gen)))

(defn gen-phase-generator
  [coll paras]
  (let [index (first-index is-phases-gen? coll)]
    (if (nil? index)
      (->> coll
           (gen/stagger (/ (:rate paras))))
      (let [first-half (take index coll)
            second-half (drop (+ 1 index) coll)]
        (if (empty? first-half)
          (if (empty? second-half)
            coll
            (gen/phases (nth coll index)
                        (gen-phase-generator second-half paras)))
          (if (empty? second-half)
            (gen/phases (gen-phase-generator first-half paras) (nth coll index))
            (gen/phases (gen-phase-generator first-half paras)
                        (nth coll index)
                        (gen-phase-generator second-half paras))))))))
