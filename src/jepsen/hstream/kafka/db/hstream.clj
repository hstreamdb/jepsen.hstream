(ns jepsen.hstream.kafka.db.hstream
  (:require [clojure.tools.logging :refer [info]]
            [jepsen.db :as db]
            [jepsen.hstream.kafka.db :as redpanda.db]))

(defn db
  "HStream DB for a particular version. No action is executed after the DB is ready."
  [version]
  (reify
    db/DB
    (setup! [this test node]
      (info ">>> Setting up DB: HStream" version "on node" node
            "But in fact we did nothing here."))
    (teardown! [this test node]
      (info ">>> Tearing down DB: HStream" version "on node" node
            "But in fact we did nothing here."))

    db/Process
    (start! [this test node]
      )
    (kill! [this test node]
      )

    db/Pause
    (pause! [this test node]
      )
    (resume! [this test node]
      )

    db/LogFiles
    (log-files [this test node]
      {})

    redpanda.db/DB
    (node-id [this test node]
      0)
    (topic-partition-state [this node topic-partition]
      :not-implemented)))
