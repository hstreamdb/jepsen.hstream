;; https://gist.github.com/fredyr/6345191

(ns jepsen.hstream.mvar
  (:gen-class)
  (:require [jepsen.hstream.mcons :refer :all]))

(defn empty-mvar
  []
  (let [take-sem (java.util.concurrent.Semaphore. 1)
        put-sem (java.util.concurrent.Semaphore. 1)]
    (.acquire take-sem)
    (mcons nil (mcons take-sem put-sem))))

(defn put-mvar
  [mvar val]
  (let [take-sem (mcar (mcdr mvar))
        put-sem (mcdr (mcdr mvar))]
    (.acquire put-sem)
    (set-mcar! mvar val)
    (.release take-sem)))

(defn take-mvar
  [mvar]
  (let [take-sem (mcar (mcdr mvar))
        put-sem (mcdr (mcdr mvar))]
    (.acquire take-sem)
    (let [val (mcar mvar)]
      (set-mcar! mvar nil)
      (.release put-sem)
      val)))

(defn new-mvar
  [val]
  (let [mvar (empty-mvar)]
    (put-mvar mvar val)
    mvar))
