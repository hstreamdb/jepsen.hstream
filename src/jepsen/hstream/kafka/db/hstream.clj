(ns jepsen.hstream.kafka.db.hstream
  (:require [clojure.tools.logging :refer [info]]
            [jepsen.db :as db]
            [jepsen.control :as c]
            [jepsen.control.util :as cu]
            [jepsen.hstream.kafka.db :as redpanda.db]
            [jepsen.hstream.common.utils :refer [parse-int]]
            [jepsen.hstream.legacy.nemesis :as legacy-nemesis]))

(def hstream
  "Program that launches hstream server."
  "/usr/local/bin/hstream-server")

(def node-ips
  {:n1 "172.20.0.11"
   :n2 "172.20.0.12"
   :n3 "172.20.0.13"
   :n4 "172.20.0.14"
   :n5 "172.20.0.15"
   })

(defn f-hstream-log-file
  "Generate the name of hstream log file by node."
  [node]
  (str "/tmp/" node ".log"))

(defn f-hstream-pid-file
  "Generate the name of hstream pid file by node."
  [node]
  (str "/tmp/" node ".pid"))

(defn f-hstream-args
  "Generate the arguments for hstream server."
  [node]
  ["kafka"
   :--config-path "/etc/hstream/config.yaml"
   :--bind-address "0.0.0.0"
   :--port 9092
   :--gossip-port 6571
   :--advertised-address (node-ips (keyword node))
   :--store-config "zk:zookeeper:2181/logdevice.conf"
   :--metastore-uri "zk://zookeeper:2181"
   :--server-id (parse-int (subs node 1))
   :--log-level "debug"
   :--log-with-color
   :--seed-nodes "hserver-1,hserver-2,hserver-3,hserver-4,hserver-5"
   ])

(defn db
  "HStream DB for a particular version. No action is executed after the DB is ready."
  [version tcpdump]
  (reify
    db/DB
    (setup! [this test node]
      (when tcpdump
        (db/setup! (db/tcpdump {:ports []}) test node))
      (info ">>> Setting up DB: HStream" version "on node" node
            "But in fact we did nothing here."))
    (teardown! [this test node]
      (when tcpdump
        (db/teardown! (db/tcpdump {:ports []}) test node))
      (info ">>> Tearing down DB: HStream" version "on node" node
            "But in fact we did nothing here."))

    db/Process
    (start! [this test node]
      (c/su
       (apply (partial cu/start-daemon!
                       {:logfile (f-hstream-log-file node)
                        :pidfile (f-hstream-pid-file node)
                        :chdir "/"
                        :make-pidfile? true}
                       hstream)
              (f-hstream-args node))))
    (kill! [this test node]
      (c/su
       (cu/stop-daemon! hstream (f-hstream-pid-file node))))

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
