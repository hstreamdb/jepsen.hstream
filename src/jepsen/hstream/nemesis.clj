(ns jepsen.hstream.nemesis
  (:gen-class)
  (:require [clojure.tools.logging :refer :all]
            [slingshot.slingshot :refer [try+]]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [control :as c] [generator :as gen]
             [independent :as independent] [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.mvar :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.net :as net]))

(defn kill-node
  [node]
  (try+ (c/on node
             (c/exec* "killall"
                      "-9" "hstream-server"
                      "&&" "killall"
                      "-9" "hstream-server"
                      "||" "true"))
       (Thread/sleep 2000) ;; is this necessasy?
       (catch Object _
         (info "Failed to kill" node ":" (:message &throw-context)
               "Ignored it because the node seems already dead."))))

(defn is-hserver-on-node-dead?
  [node]
  (try+
    (let [shell-out (c/on node
                          (c/exec* "pgrep" "-x" "hstream-server" "||" "true"))]
      (empty? shell-out))
    (catch Object _
      (info "Failed to check death on" node ":" (:message &throw-context)
            "I think it is already dead.")
      true)))

(defn is-hserver-on-node-alive?
  [node]
  (try+ (let [shell-out
               (c/on node (c/exec* "pgrep" "-x" "hstream-server" "||" "true"))]
         (seq shell-out))
       (catch Object _
         (info "Failed to check liveness on" node ":" (:message &throw-context)
               "I think it is already dead.")
         false)))

(defn restart-node
  [node]
  (try+ (c/on node (c/exec* "/bin/start-server"))
       (Thread/sleep 5000) ;; It may take a while for the server to join the cluster
       (catch Object _
         (warn "Failed to restart" node ":" (:message &throw-context)
               "Ignored it but you should remember I have tried!"))))

(defn find-hserver-alive-nodes
  [test]
  (into []
        (filter is-hserver-on-node-alive?
          (remove #{"zk" "ld1" "ld2" "ld3"} (:nodes test)))))
(defn find-hserver-dead-nodes
  [test]
  (into []
        (filter is-hserver-on-node-dead?
          (remove #{"zk" "ld1" "ld2" "ld3"} (:nodes test)))))

(defn hserver-killer
  ([] (hserver-killer 1)) ;; default minimal alive nodes is 1
  ([min-nodes]
   (reify
     nemesis/Nemesis
       (nemesis/setup! [this _] this)
       (nemesis/invoke! [_ test op]
         (case (:f op)
           :kill-node (let [alive-nodes (find-hserver-alive-nodes test)]
                        (if (<= (count alive-nodes) min-nodes)
                          (assoc op :value "killing skipped")
                          (let [node (rand-nth alive-nodes)]
                            (kill-node node)
                            (assoc op
                              :value (str "killed " node)
                              :node node))))
           :resume-node (let [dead-nodes (find-hserver-dead-nodes test)]
                          (if (empty? dead-nodes)
                            (assoc op :value "restarting skipped")
                            (let [node (rand-nth dead-nodes)]
                              (restart-node node)
                              (assoc op
                                :value (str "restarted " node)
                                :node node))))))
       (nemesis/teardown! [_ _]))))

(defn split-one-hserver-node
  "Split one node off from the rest.
   It ensures that the loner is always a hserver node."
  [nodes]
  (let [hserver-nodes (remove #{"zk" "ld1" "ld2" "ld3"} nodes)
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
  (nemesis/compose {#{:kill-node :resume-node} (hserver-killer 1),
                    #{:start-slow :stop-slow} (slower),
                    #{:start-loss :stop-loss} (losser),
                    {:isolate-zk :start, :resume-zk :stop} (zk-nemesis)}))

(def plot-spec
  "Specification for how to render operations in plots"
  {:nemeses #{{:name "kill node",
               :color "#E9A4A0",
               :start #{:kill-node},
               :stop #{:resume-node}}
              {:name "isolate zk",
               :color "#F8B500",
               :start #{:isolate-zk},
               :stop #{:resume-zk}}
              {:name "slow network",
               :color "#C5A0E9",
               :start #{:start-slow},
               :stop #{:stop-slow :stop-loss}}
              {:name "packet loss",
               :color "#A0E9DB",
               :start #{:start-loss},
               :stop #{:stop-loss :stop-slow}}}})
