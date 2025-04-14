(ns jepsen.hstream.kafka.db.flowmq
  (:require [clojure.tools.logging :refer [info]]
            [slingshot.slingshot :refer [try+]]
            [jepsen.db :as db]
            [jepsen.control :as c]
            [jepsen.control.util :as cu]
            [jepsen.hstream.kafka.db :as redpanda.db]
            [jepsen.hstream.common.utils :refer [parse-int]]))

(def flowmq
  "Program that launches flowmq server."
  "/usr/local/bin/flowmq")

(def node-ips
  {:n1 "172.20.0.11"
   })

(defn f-flowmq-log-file
  "Generate the name of flowmq log file by node."
  [node]
  (str "/tmp/" node ".log"))

(defn f-flowmq-pid-file
  "Generate the name of flowmq pid file by node."
  [node]
  (str "/tmp/" node ".pid"))

(defn f-flowmq-args
  "Generate the arguments for flowmq server."
  [node]
  [:-C "/etc/fdb.cluster"
   :--storage "fdb"
   :--kafka-advertised-address (node-ips (keyword node))
   :--with-kafka 9092
   :--with-amqp 5672
   ])

;; FIXME: move to a standalone module to support hornbill nemesis
(defn is-flowmq-server-on-node-dead?
  [node]
  (try+
    (let [shell-out (c/on node
                          (c/exec* "pgrep" "-f" "flowmq" "||" "true"))]
      (empty? shell-out))
    (catch Object _
      (info "Failed to check death on" node ":" (:message &throw-context)
            "I think it is already dead.")
      true)))

(defn db
  "flowmq for a particular version. No action is executed after the DB is ready."
  [version tcpdump]
  (reify
    db/DB
    (setup! [this test node]
      (when tcpdump
        (db/setup! (db/tcpdump {:ports []}) test node))
      (info ">>> Setting up DB: flowmq" version "on node" node
            "But in fact we did nothing here."))
    (teardown! [this test node]
      (when tcpdump
        (db/teardown! (db/tcpdump {:ports []}) test node))
      (info ">>> Tearing down DB: flowmq" version "on node" node
            "But in fact we did nothing here."))

    db/Process
    ;; WARNING: Starting flowmq server is not idempotent now.
    ;;          However, the test usually call [:start :all].
    ;;          So we have to check if the server is already running.
    ;; FIXME: The checking function 'is-flowmq-server-on-node-dead?' is not
    ;;        well implemented...
    (start! [this test node]
      (if (is-flowmq-server-on-node-dead? node)
        (c/su
         (apply (partial cu/start-daemon!
                         {:logfile (f-flowmq-log-file node)
                          :pidfile (f-flowmq-pid-file node)
                          :chdir "/"
                          :make-pidfile? true}
                         flowmq)
                (f-flowmq-args node)))
        :skipped-by-us))
    (kill! [this test node]
      (c/su
       (cu/stop-daemon! flowmq (f-flowmq-pid-file node))))

    db/Pause
    (pause! [this test node]
      )
    (resume! [this test node]
      )

    db/LogFiles
    (log-files [this test node]
      (when tcpdump
        (db/log-files (db/tcpdump {:ports []}) test node))
      {})

    redpanda.db/DB
    (node-id [this test node]
      0)
    (topic-partition-state [this node topic-partition]
      :not-implemented)))
