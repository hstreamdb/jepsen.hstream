(ns jepsen.hstream.legacy.common
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :refer :all]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [generator :as gen] [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.hstream.legacy.client :refer :all]
            [jepsen.hstream.common.mvar :refer :all]
            [jepsen.hstream.legacy.nemesis :as local-nemesis]
            [slingshot.slingshot :refer [throw+ try+]]
            [jepsen.hstream.common.utils :refer :all])
  (:import (io.grpc StatusException)
           (java.util.concurrent TimeoutException
                                 TimeUnit
                                 CompletionException)
           (java.net ConnectException)
           (io.hstream HStreamDBClientException)))

(defmacro op-with-errors
  [op & body]
  `(try+ ~@body
         (catch StatusException e#
           (case (.getStatus e#)
             Status$Code/UNAVAILABLE         (assoc ~op :type :info :error :grpc-unavailable)
             Status$Code/ABORTED             (assoc ~op :type :info :error :grpc-aborted)
             Status$Code/DEADLINE_EXCEEDED   (assoc ~op :type :info :error :grpc-unavailable)
             Status$Code/FAILED_PRECONDITION (assoc ~op :type :info :error :grpc-failed-precondition)
             (assoc ~op :type :fail :error [:grpc-bad-exceptions (.getDescription (.getStatus e#))])))
         (catch CompletionException e#
           (assoc ~op :type :info :error [:completion-exceptions e#]))
         (catch HStreamDBClientException e#
           (condp re-find (.getMessage e#)
             #"UNAVAILABLE"
             (assoc ~op :type :info :error :grpc-unavailable)

             (assoc ~op :type :fail :error [:hstream-exceptions e#])))
         (catch TimeoutException e#
           (assoc ~op :type :info :error [:timeout-exceptions e#]))
         (catch IllegalStateException e#
           (assoc ~op :type :info :error [:illegal-state-exceptions e#]))
         (catch ConnectException e#
           (assoc ~op :type :info :error [:connect-exceptions e#]))
         (catch Object e#
           (assoc ~op :type :fail :error [:other-exceptions e#]))))

(defn db-with-streams-initialized
  "HStream DB for a particular version. Here we use the FIRST
   node to create streams for the whole test."
  [version opts streams]
  (reify
    db/DB
    (setup! [_ test node]
      (when (= node "n1")
        (let [service-url (str "hstream://" node ":6570")
              this-client (get-client-until-ok service-url
                                               (* 1000 (:grpc-timeout opts)))]
          (dosync (dorun (map #(try+ (create-stream this-client
                                                    %
                                                    (:max-partitions opts))
                                     (catch Object _ nil))
                              streams))))))
    (teardown! [_ _ node])))

(defn db-empty
  "HStream DB for a particular version. No extra action is executed after the DB is ready."
  [version]
  (reify
    db/DB
    (setup! [_ _ _])
    (teardown! [_ _ _])))

(defn client-on-node
  "Open a hstream client on a particular node, which may not be a hserver node.
   If not, randomly pick an alive hserver node instead.
   Returns: (assoc this :client client :target-node actual-node)
   Warning: `client` and `actual-node` may be nil if there is no alive node."
  [this test node]
  (try+
   (let [target-node (if (is-hserver-node? node)
                       node
                       (let [alive-nodes
                             (local-nemesis/find-hserver-alive-nodes test)
                             ]
                         (if (empty? alive-nodes)
                           nil
                           (rand-nth alive-nodes))))
         ]
     (if (nil? target-node)
       (assoc this :client nil :target-node nil)
       (let [service-url (str "hstream://" target-node ":6570")
             [got-node got-client] (get-client-start-from-url
                                    service-url
                                    (* 1000 (:grpc-timeout (:opts this))))]
         (if (nil? got-client)
           (assoc this :client nil :target-node nil)
           (assoc this :client got-client :target-node got-node)))))
   (catch RuntimeException e
     (condp re-find (.getMessage e)
       #"ManagedChannel"
       (do (Thread/sleep 1000)
           (client-on-node this test node))

       (assoc this :client nil :target-node nil)))
   (catch Object _
     (assoc this :client nil :target-node nil))))

(defrecord Default-Client [opts subscription-results subscription-ack-timeout]
  client/Client
    (open! [this test node]
      (client-on-node this test node))
    (setup! [_ _])
    (invoke! [this _ op]
      (let [op (assoc op :target-node (:target-node this))]
        (cond
          (nil? (:client this))
          (assoc op :type :info :error :no-alive-node)

          :else
          (case (:f op)
            :add (op-with-errors op
                   (let [test-data {:key (:value op)}
                         producer (create-producer (:client this)
                                                   (:stream op)
                                                   (* 1000 (:grpc-timeout opts)))
                         write-future (if (zero? (:max-partitions opts))
                                        (write-data producer test-data)
                                        (let [partition-key (-> (:value op)
                                                                (+ (rand-int (:max-partitions opts)))
                                                                (mod (:max-partitions opts))
                                                                (str))
                                              ]
                                          (write-data producer test-data partition-key)))
                         ]
                     (-> write-future
                         (.orTimeout (:write-timeout opts) TimeUnit/SECONDS)
                         (.join)
                         (#(assoc op :type :ok :record-id %)))))
            :sub (op-with-errors op
                   (let [test-subscription-id (str "subscription_" (:stream op))]
                     (subscribe (:client this)
                                test-subscription-id
                                (:stream op)
                                subscription-ack-timeout)
                     (assoc op :type :ok)))
            :create (op-with-errors op
                      (do (create-stream (:client this)
                                         (:stream op)
                                         (:max-partitions opts))
                          (Thread/sleep (* 1000 5))
                          ;; Very important: wait for the stream to be ready. Or creating subs will be very slow!
                          (assoc op :type :ok)))
            :read   (op-with-errors op
                      (let [subscription-result (get subscription-results
                                                     (:consumer-id op))
                            test-subscription-id (str "subscription_" (:stream op))
                            consumer (consume (:client this)
                                              test-subscription-id
                                              (gen-collect-value-callback
                                               subscription-result))]
                        (Thread/sleep (* 1000 (:fetch-wait-time opts)))
                        (.awaitTerminated (.stopAsync consumer))
                        (assoc op :type :ok :value @subscription-result)))))))
    (teardown! [_ _])
    (close! [this _]
      (try+ (.close (:client this))
            (catch Object _))))

(def cli-opts
  "Additional command line options."
  [[nil "--grpc-timeout SECOND" "The timeout of gRPC client." :default 5
    :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-f" "--fetch-wait-time SECOND"
    "The time between starting fetching from the stream and shutting down it."
    :default 15 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--dummy BOOL" "Whether to use dummy ssh connection for local test."
    :default false :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean"]]
   [nil "--write-timeout SECOND" "The max time for a single write operation."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--max-partitions INT"
    "The maximum number of partitions(ordering keys). 0 means use default key only"
    :default 0 :parse-fn read-string :validate
    [#(and (number? %) (>= % 0)) "Must be a non-negative number"]]
   [nil "--nemesis-on [true|false]" "Whether to turn on the nemesis" :default
    true :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean value"]]
   [nil "--nemesis-interval SECOND"
    "The interval between two nemesis operations." :default 15 :parse-fn
    read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]])
