(ns jepsen.hstream-fetches
  (:gen-class)
  (:require [clojure.string     :as str]
            [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
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
            [slingshot.slingshot :refer [try+ throw+]]
            [knossos.model      :as model]
            [random-string.core :as rs]

            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.hstream.checker :as local-checker]
            [jepsen.hstream.mvar :refer :all]
            [clojure.stacktrace :refer [e]]
            [jepsen.hstream.common-test :as common]
            [jepsen.hstream.nemesis :as local-nemesis])
  (:import [io.hstream HRecord]))

;;;;;;;;;; Global Variables ;;;;;;;;;;

;; Streams

(def test-stream-name-length
  "The length of random stream name. It should be a positive natural
   and not too small to avoid name confliction."
  20)

;; Read: Subscriptions & Related Data
(def subscription-timeout
  "The timeout of subscriptions in SECOND."
  600)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gen-adds [streams]
  (->> (range)
       (map (fn [x] {:type :invoke, :f :add, :value x, :stream (rand-nth streams)}))))
(defn gen-read [stream id]
  (gen/once {:type :invoke, :f :read, :value nil, :consumer-id id, :stream stream}))
(defn gen-add [stream x]   (gen/once {:type :invoke, :f :add, :value x, :stream stream}))
(defn gen-sub [stream] (gen/once {:type :invoke, :f :sub, :value stream, :stream stream}))

(defrecord Client [opts test-streams subscription-results futures]
  client/Client

  (open! [this test node]
    (let [target-node (if (local-nemesis/is-node-alive node)
                        node
                        (rand-nth (local-nemesis/find-alive-nodes test))) ;; FIXME: Empty list!
          service-url (str target-node ":6570")
          client      (try+ (get-client service-url) (catch Exception e (warn e)))]
      (-> this
          (assoc :client client :target-node target-node))))

  (setup! [this _]
    (info "-------- SETTING UP DONE ---------"))

  (invoke! [this _ op]
    (try+
     (case (:f op)
       :add (let [is-done (agent nil)
                  test-data {:key (:value op)}]
              (send-off is-done
                        (fn [_]
                          (try+
                           (let [producer     (create-producer (:client this) (:stream op))
                                 write-future (write-data producer test-data)]
                             (if (:async-write opts)
                               (dosync (alter futures assoc (:client this) write-future))
                               (.join write-future))
                             {:status :done :details nil})
                           (catch Exception e {:status :error :details e}))))
              (if (await-for (* 1000 (:write-timeout opts)) is-done)
                (let [done-result @is-done]
                  (case (:status done-result)
                    :done  (assoc op :type :ok :target-node (:target-node this))
                    :error (assoc op :type :fail :error (:details done-result) :target-node (:target-node this))))
                (assoc op :type :fail :error :unknown-timeout :target-node (:target-node this))))
       :sub (let [test-subscription-id (str "subscription_" (:stream op))]
              (subscribe (:client this)
                         test-subscription-id
                         (:stream op)
                         :earliest
                         subscription-timeout)
              (assoc op :type :ok :sub-id test-subscription-id :target-node (:target-node this)))
       :read (let [is-done              (agent false)
                   subscription-result  (get subscription-results (:consumer-id op))
                   test-subscription-id (str "subscription_" (:stream op))]
               (consume (:client this)
                        test-subscription-id
                        (gen-collect-value-callback subscription-result))
               (send-off is-done (fn [_]
                                   (Thread/sleep (* 1000 (:fetch-wait-time opts)))
                                   true))
               (await is-done)
               (assoc op :type :ok :value @subscription-result :target-node (:target-node this))
               ))
     (catch java.net.SocketTimeoutException e
       (assoc op :type :fail :error :socket-timeout :target-node (:target-node this)))
     (catch Exception e
       (warn "---> Err when invoking an operation:" e)
       (assoc op :type :fail :error e :target-node (:target-node this)))))

  (teardown! [this _]
    )

  (close! [this _]
    (try+
     (dosync (println ">>> Closing client...")
             (let [write-future (get @futures (:client this))]
               (when (not (nil? write-future))
                 (.join write-future)))
             (.close (:client this)))
     (catch Exception e nil))
    ))

(defn hstream-test
  "Given an options map from the command-line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [; "The random-named stream names. It is a vector of strings."
        test-streams (into [] (repeatedly (:max-streams opts)
                                          #(rs/string test-stream-name-length)))
        subscription-results (into [] (repeatedly (:fetching-number opts) #(ref [])))
        futures (ref {})]

    (merge tests/noop-test
           opts
           {:pure-generators true
            :name    "HStream"
            :db      (common/db "0.6.0" test-streams)
            :client  (Client. opts test-streams subscription-results futures)
            :nemesis (local-nemesis/nemesis+)
            :ssh {:dummy? (:dummy opts)}
            :checker (checker/compose
                      {:set (local-checker/set+)
                       :stat (checker/stats)
                       :latency (checker/latency-graph)
                       :rate (checker/rate-graph)
                       :clock (checker/clock-plot)
                       :exceptions (checker/unhandled-exceptions)
                       })
            :generator (gen/clients
                        ;; clients
                        (gen/phases
                         (map gen-sub test-streams)
                         (->> (gen-adds test-streams)
                                        (gen/stagger (/ (:rate opts)))
                                        (gen/time-limit (:write-time opts)))
                         (map #(gen-read (rand-nth test-streams) %)
                              (range 0 (:fetching-number opts)))
                         (gen/sleep (+ 10 (:fetch-wait-time opts))))
                        ;; nemesis
                        (->> (gen/phases (gen/sleep 10)
                                         (gen/mix [(repeat {:type :info :f :start})
                                                   (repeat {:type :info :f :stop})]))
                             (gen/stagger (:nemesis-interval opts))
                             (gen/time-limit (+ (:write-time opts)
                                                (:fetch-wait-time opts))))
                        )})))

(def cli-opts
  "Additional command line options."
  (concat common/cli-opts
          [[nil "--fetching-number INT" "The number of fetching operations in total."
            :default  10
            :parse-fn read-string
            :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
           ["-w" "--write-time SECOND" "The whole time to write data into database."
            :default  20
            :parse-fn read-string
            :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
           [nil "--write-timeout SECOND" "The max time for a single write operation."
            :default  10
            :parse-fn read-string
            :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
           [nil "--nemesis-interval SECOND" "The interval between two nemesis operations."
            :default  15
            :parse-fn read-string
            :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
           ]))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn hstream-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
