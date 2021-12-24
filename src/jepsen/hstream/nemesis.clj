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
        :start (let [alive-nodes (find-alive-nodes test)
                     _ (info "****** ALIVE NODES: " (str alive-nodes))]
                 (if (empty? alive-nodes)
                   (assoc op :value "no alive nodes")
                   (let [node (rand-nth alive-nodes)
                         _ (info (str "******" node "******"))]
                     (kill-node node)
                     (assoc op :value "killed"))))
        :stop (let [dead-nodes (find-dead-nodes test)
                    _ (info "****** DEAD NODES: " (str dead-nodes))]
                (if (seq dead-nodes)
                  (let [node (rand-nth dead-nodes)
                        _ (info (str "******" node "******"))
                        _ (info "RESTARTING>>>")
                        log (restart-node node)
                        _ (info (str "Logs: " log))]
                    (assoc op :value "restarted"))
                  (assoc op :value "no dead nodes"))
                )))

    (nemesis/teardown! [this test]
      )
    ))
