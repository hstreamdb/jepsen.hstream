(ns jepsen.hstream.nemesis
  (:gen-class)
  (:require [clojure.string     :as str]
            [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :as pp]
            [jepsen [db         :as db]
                    [cli        :as cli]
                    [checker    :as checker]
                    [client     :as client]
                    [control    :as c]
                    [generator  :as gen]
                    [independent :as independent]
                    [nemesis    :as nemesis]
                    [tests      :as tests]
             [util       :refer [timeout]]]
            [jepsen.os.ubuntu   :as ubuntu]
            [jepsen.checker.timeline :as timeline]
            [slingshot.slingshot :refer [try+]]
            [knossos.model      :as model]
            [random-string.core :as rs]
            [jepsen.control.docker :as docker]
            [jepsen.control.core :as cc]

            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.hstream.mvar :refer :all]
            [clojure.stacktrace :refer [e]]))

(defn kill-node
  [node]
  (c/on node (c/exec "killall" "-9" "hstream-server" "&&" "killall" "-9" "hstream-server" "||" "true")))

(defn is-node-dead
  [node]
  (let [shell-out (c/on node (c/exec "pgrep" "-x" "hstream-server" "||" "true"))]
    (empty? shell-out)))

(defn is-node-alive
  [node]
  (let [shell-out (c/on node (c/exec "pgrep" "-x" "hstream-server" "||" "true"))]
    (seq shell-out)))

(defn restart-node
  [node]
  (c/on node (c/exec "/bin/start-hstream-server")))

(defn find-alive-nodes
  [test]
  (into [] (filter is-node-alive (:nodes test))))

(defn find-dead-nodes
  [test]
  (into [] (filter is-node-dead (:nodes test))))

(defn nemesis+ []
  (reify nemesis/Nemesis

    (nemesis/setup! [this test]
      this)

    (nemesis/invoke! [this test op]
      (case (:f op)
        :start (let [alive-nodes (find-alive-nodes test)]
                 (if (<= (count alive-nodes) 1)
                   (assoc op :value "killing skipped")
                   (let [node (rand-nth alive-nodes)]
                     (kill-node node)
                     (assoc op :value "killed" :node node))))
        :stop (let [dead-nodes (find-dead-nodes test)]
                (if (empty? dead-nodes)
                  (assoc op :value "restarting skipped")
                  (let [node (rand-nth dead-nodes)]
                    (restart-node node)
                    (assoc op :value "restarted" :node node))))))

    (nemesis/teardown! [this test]
      )
    ))
