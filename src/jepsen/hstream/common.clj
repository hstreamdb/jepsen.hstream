(ns jepsen.hstream.common
  (:gen-class)
  (:require [clojure.stacktrace :refer [e]]
            [clojure.tools.logging :refer :all]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [generator :as gen] [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.mvar :refer :all]
            [jepsen.hstream.nemesis :as local-nemesis]
            [jepsen.hstream.utils :refer :all]
            [slingshot.slingshot :refer [try+]]))

(defn db-with-streams-initialized
  "HStream DB for a particular version. Here we use the FIRST
   node to create streams for the whole test."
  [version streams]
  (reify
    db/DB
      (setup! [_ test node]
        (info node ">>> Setting up DB: HStream" version)
        (when (= node (first (:nodes test)))
          (let [service-url (str node ":6570")
                client (get-client service-url)]
            (dosync (dorun (map #(try+ (create-stream client %)
                                       (catch Exception e nil))
                             streams))))))
      (teardown! [_ _ node]
        (info node ">>> Tearing down DB: HStream" version))))

(defn db-empty
  "HStream DB for a particular version. No extra action is executed after the DB is ready."
  [version]
  (reify
    db/DB
      (setup! [_ _ node] (info node ">>> Setting up DB: HStream" version))
      (teardown! [_ _ node]
        (info node ">>> Tearing down DB: HStream" version))))

(defrecord Default-Client [opts subscription-results subscription-timeout]
  client/Client
    (open! [this test node]
      (let [target-node (if (local-nemesis/is-hserver-on-node-alive? node)
                          node
                          (rand-nth (local-nemesis/find-hserver-alive-nodes
                                      test))) ;; FIXME:
            ;; Empty
            ;; list!
            service-url (str target-node ":6570")
            client (get-client-until-ok service-url)]
        (-> this
            (assoc :client client
                   :target-node target-node))))
    (setup! [_ _] (info "-------- SETTING UP DONE ---------"))
    (invoke! [this test op]
      (try
        (case (:f op)
          :add (let [is-done (agent nil)
                     test-data {:key (:value op)}
                     producer (create-producer (:client this) (:stream op))]
                 (send-off
                   is-done
                   (fn [_]
                     (try (let [write-future
                                  (if (zero? (:max-partitions opts))
                                    (write-data producer test-data)
                                    (write-data
                                      producer
                                      test-data
                                      ;; orderingKey
                                      (str (mod (+ (:value op)
                                                   (rand-int (:max-partitions
                                                               opts)))
                                                (:max-partitions opts)))))]
                            (.join write-future)
                            {:status :done, :details nil})
                          (catch java.util.concurrent.CompletionException e
                            (if (is-hstream-client-unavailable-exception? e)
                              {:status :retry, :exception e}
                              {:status :error, :details (Throwable->map e)}))
                          (catch Exception e
                            {:status :error, :details (Throwable->map e)}))))
                 (if (await-for (* 1000 (:write-timeout opts)) is-done)
                   (let [done-result @is-done]
                     (case (:status done-result)
                       :done (assoc op
                               :type :ok
                               :target-node (:target-node this))
                       :error (assoc op
                                :type :fail
                                :error (:details done-result)
                                :target-node (:target-node this)
                                :extra "happened in send-off")
                       :retry (throw (:exception done-result))))
                   (assoc op
                     :type :fail
                     :error :unknown-timeout
                     :target-node (:target-node this))))
          :sub (let [test-subscription-id (str "subscription_" (:stream op))]
                 (subscribe (:client this)
                            test-subscription-id
                            (:stream op)
                            subscription-timeout)
                 (assoc op
                   :type :ok
                   :sub-id test-subscription-id
                   :target-node (:target-node this)))
          :create (do (create-stream (:client this) (:stream op))
                      (assoc op
                        :type :ok
                        :target-node (:target-node this)))
          :read
            (let [is-done (agent false)
                  subscription-result (get subscription-results
                                           (:consumer-id op))
                  test-subscription-id (str "subscription_" (:stream op))]
              (consume (:client this)
                       test-subscription-id
                       (gen-collect-value-callback subscription-result))
              (send-off
                is-done
                (fn [_] (Thread/sleep (* 1000 (:fetch-wait-time opts))) true))
              (await is-done)
              (assoc op
                :type :ok
                :value @subscription-result
                :target-node (:target-node this))))
        (catch java.util.concurrent.CompletionException e
          (let [old-op-retry-times
                  (if (nil? (:retry-times op)) 0 (:retry-times op))]
            (if (and (is-hstream-client-unavailable-exception? e)
                     (< old-op-retry-times (:max-retry-times opts)))
              (let [new-target-node
                      (rand-nth (local-nemesis/find-hserver-alive-nodes test))
                    new-client (get-client-until-ok (str new-target-node
                                                         ":6570"))
                    new-this (-> this
                                 (assoc :target-node new-target-node
                                        :client new-client))]
                (Thread/sleep 1000)
                (client/invoke! new-this
                                test
                                (assoc op
                                  :retry? true
                                  :retry-times (+ 1 old-op-retry-times))))
              (assoc op
                :type :fail
                :error (Throwable->map e)
                :target-node (:target-node this)
                :extra "happened in op"))))
        (catch java.net.SocketTimeoutException e
          (assoc op
            :type :fail
            :error :socket-timeout
            :target-node (:target-node this)))
        (catch Exception e
          (assoc op
            :type :fail
            :error (Throwable->map e)
            :target-node (:target-node this)
            :extra "happened in op"))))
    (teardown! [_ _])
    (close! [this _]
      (try (dosync (println ">>> Closing client...") (.close (:client this)))
           (catch Exception e nil))))

(def cli-opts
  "Additional command line options."
  [["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-f" "--fetch-wait-time SECOND"
    "The time between starting fetching from the stream and shutting down it."
    :default 15 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--dummy BOOL" "Whether to use dummy ssh connection for local test."
    :default false :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean"]]
   [nil "--max-retry-times INT"
    "The maximum retry times of every operation in the test." :default 5
    :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
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
