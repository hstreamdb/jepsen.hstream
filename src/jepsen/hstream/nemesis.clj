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


(defn nemesis+ []
  (reify nemesis/Nemesis

    (nemesis/setup! [this test]
      this)

    (nemesis/invoke! [this test op]
      (case (:f op)
        :start (let [node (rand-nth (:nodes test))
                     _ (info (str "******" node "******"))
                     shell-out (c/on node (c/exec ["pgrep" "-x" "hstream-server" "||" "true"]))
                     server-dead (empty? shell-out)
                     _ (info shell-out)]
                 (if server-dead
                   (assoc op :value "already dead")
                   (do (c/on node (c/exec ["killall" "-9" "hstream-server"]))
                       (assoc op :value "killed"))))
        :stop (let [node (rand-nth (:nodes test))
                    _ (info (str "******" node "******"))
                    shell-out (c/on node (c/exec ["pgrep" "-x" "hstream-server" "||" "true"]))
                    server-dead (empty? shell-out)
                    _ (info shell-out)]
                (if server-dead
                  (do (c/on node (c/exec ["hstream-server" "--host" "0.0.0.0"
                                          "--port" "6570" "--address" "$(hostname -I)"
                                          "--store-config" "/root/logdevice.conf"
                                          "--zkuri" "zookeeper:2188"
                                          "--server-id" "$(shuf -i 1-4294967296 -n 1)"
                                          "--log-level" "debug"
                                          "--log-with-color"
                                          "&"]))
                      (assoc op :value "restarted"))
                  (assoc op :value "already up")))
        ))

    (nemesis/teardown! [this test]
      )
    ))
