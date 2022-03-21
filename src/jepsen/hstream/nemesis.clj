(ns jepsen.hstream.nemesis
  (:gen-class)
  (:require [clojure.tools.logging :refer :all]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [control :as c] [generator :as gen]
             [independent :as independent] [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.mvar :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.net :as net]
            [jepsen.nemesis :as nemesis]))

(defn kill-node
  [node]
  (c/on node
        (c/exec* "killall"
                 "-9" "hstream-server"
                 "&&" "killall"
                 "-9" "hstream-server"
                 "||" "true")))

(defn is-hserver-on-node-dead?
  [node]
  (let [shell-out (c/on node
                        (c/exec* "pgrep" "-x" "hstream-server" "||" "true"))]
    (empty? shell-out)))

(defn is-hserver-on-node-alive?
  [node]
  (let [shell-out (c/on node
                        (c/exec* "pgrep" "-x" "hstream-server" "||" "true"))]
    (seq shell-out)))

(defn restart-node [node] (c/on node (c/exec* "/bin/start-hstream-server")))

(defn find-hserver-alive-nodes
  [test]
  (into []
        (filter is-hserver-on-node-alive? (remove #{"zk" "ld"} (:nodes test)))))
(defn find-hserver-dead-nodes
  [test]
  (into []
        (filter is-hserver-on-node-dead? (remove #{"zk" "ld"} (:nodes test)))))

(defn hserver-killer
  []
  (reify
    nemesis/Nemesis
      (nemesis/setup! [this _] this)
      (nemesis/invoke! [_ test op]
        (case (:f op)
          :kill-node (let [alive-nodes (find-hserver-alive-nodes test)]
                       (if (<= (count alive-nodes) 1)
                         (assoc op :value "killing skipped")
                         (let [node (rand-nth alive-nodes)]
                           (kill-node node)
                           (assoc op
                             :value "killed"
                             :node node))))
          :resume-node (let [dead-nodes (find-hserver-dead-nodes test)]
                         (if (empty? dead-nodes)
                           (assoc op :value "restarting skipped")
                           (let [node (rand-nth dead-nodes)]
                             (restart-node node)
                             (assoc op
                               :value "restarted"
                               :node node))))))
      (nemesis/teardown! [_ _])))

(defn split-one-hserver-node
  "Split one node off from the rest.
   It ensures that the loner is always a hserver node."
  [nodes]
  (let [hserver-nodes (remove #{"zk" "ld"} nodes)
        loner (rand-nth hserver-nodes)]
    [[loner] (remove (fn [x] (= x loner)) nodes)]))

(defn zk-hserver-grudge
  "Takes a collection of components in the form of [[loner] '(others)],
   and computes a grudge such that the loner can not talk from and to
   the zk node. The result is in the form of {loner #{zk}, zk #{loner}}."
  [components]
  (let [[loner-vec _] components
        [loner] loner-vec]
    (assoc {}
      loner #{"zk"}
      "zk" #{loner})))

(defn zk-nemesis
  []
  (nemesis/partitioner (comp zk-hserver-grudge split-one-hserver-node)))

(defn slower
  []
  (reify
    nemesis/Nemesis
      (nemesis/setup! [this test] (net/fast! (:net test) test) this)
      (nemesis/invoke! [this test op]
        (case (:f op)
          :start-slow (do (net/slow! (:net test) test)
                          (assoc op :value :slowed-by-50ms))
          :stop-slow (do (net/fast! (:net test) test)
                         (assoc op :value :network-resumed))))
      (nemesis/teardown! [this test] (net/fast! (:net test) test))))

(defn losser
  []
  (reify
    nemesis/Nemesis
      (nemesis/setup! [this test] (net/fast! (:net test) test) this)
      (nemesis/invoke! [this test op]
        (case (:f op)
          :start-loss (do (net/flaky! (:net test) test)
                          (assoc op :value :loss-by-20%))
          :stop-loss (do (net/fast! (:net test) test)
                         (assoc op :value :network-resumed))))
      (nemesis/teardown! [this test] (net/fast! (:net test) test))))

(defn nemesis+
  []
  (nemesis/compose {#{:kill-node :resume-node} (hserver-killer),
                    #{:start-slow :stop-slow} (slower),
                    #{:start-loss :stop-loss} (losser),
                    {:isolate-zk :start, :resume-zk :stop} (zk-nemesis)}))
