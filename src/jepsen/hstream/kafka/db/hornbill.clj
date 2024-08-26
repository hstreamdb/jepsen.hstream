(ns jepsen.hstream.kafka.db.hornbill
  (:require [clojure.tools.logging :refer [info]]
            [jepsen.db :as db]
            [jepsen.control :as c]
            [jepsen.control.util :as cu]
            [jepsen.hstream.kafka.db :as redpanda.db]
            [jepsen.hstream.common.utils :refer [parse-int]]
            [jepsen.hstream.legacy.nemesis :as legacy-nemesis]))

(def hornbill
  "Program that launches hornbill server.
  WARNING: This module refers to jepsen.hstream.legacy.nemesis,
  which hardcodes the server name as 'hstream-server'."
  "/usr/local/bin/hstream-server")

(def node-ips
  {:n1 "172.20.0.11"
   :n2 "172.20.0.12"
   :n3 "172.20.0.13"
   :n4 "172.20.0.14"
   :n5 "172.20.0.15"
   })

(defn f-hornbill-log-file
  "Generate the name of hornbill log file by node."
  [node]
  (str "/tmp/" node ".log"))

(defn f-hornbill-pid-file
  "Generate the name of hornbill pid file by node."
  [node]
  (str "/tmp/" node ".pid"))

(defn f-hornbill-args
  "Generate the arguments for hornbill server."
  [node]
  [:--bind-address "0.0.0.0"
   :--port 9092
   :--metrics-port 6600
   :--advertised-address (node-ips (keyword node))
   :--meta-servers "http://meta:8964"
   :--store-config "/etc/fdb.cluster"
   :--server-id (parse-int (subs node 1))
   :--log-level "debug"
   :--log-with-color
   ])

(defn db
  "Hornbill for a particular version. No action is executed after the DB is ready."
  [version tcpdump]
  (reify
    db/DB
    (setup! [this test node]
      (when tcpdump
        (db/setup! (db/tcpdump {:ports []}) test node))
      (info ">>> Setting up DB: Hornbill" version "on node" node
            "But in fact we did nothing here."))
    (teardown! [this test node]
      (when tcpdump
        (db/teardown! (db/tcpdump {:ports []}) test node))
      (info ">>> Tearing down DB: Hornbill" version "on node" node
            "But in fact we did nothing here."))

    db/Process
    ;; WARNING: Starting hstream server is not idempotent now.
    ;;          However, the test usually call [:start :all].
    ;;          So we have to check if the server is already running.
    ;; FIXME: The checking function 'is-hserver-on-node-dead?' is not
    ;;        well implemented...
    ;; FIXME: Remove dependency on legacy-nemesis
    (start! [this test node]
      (if (legacy-nemesis/is-hserver-on-node-dead? node)
        (c/su
         (apply (partial cu/start-daemon!
                         {:logfile (f-hornbill-log-file node)
                          :pidfile (f-hornbill-pid-file node)
                          :chdir "/"
                          :make-pidfile? true}
                         hornbill)
                (f-hornbill-args node)))
        :skipped-by-us))
    (kill! [this test node]
      (c/su
       (cu/stop-daemon! hornbill (f-hornbill-pid-file node))))

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
