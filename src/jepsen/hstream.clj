(ns jepsen.hstream
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
            [slingshot.slingshot :refer [try+]]
            [knossos.model      :as model]
            [random-string.core :as rs]

            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.hstream.checker :as local-checker]
            [jepsen.hstream.mvar :refer :all]
            [clojure.stacktrace :refer [e]]
            [jepsen.hstream.common-test :as common])
  (:import [io.hstream HRecord]))

;;;;;;;;;; Global Variables ;;;;;;;;;;

;; Streams

(def test-stream-name-length
  "The length of random stream name. It should be a positive natural
   and not too small to avoid name confliction."
  20)

;; Read: Subscriptions & Related Data
(def test-subscription-name-length
  "The length of random subscription ID. It should be a positive natural
   and not too small to avoid name confliction."
  10)

(def test-subscription-id
  "The one and only one subscription ID for test."
  (rs/string test-subscription-name-length))

(def subscription-timeout
  "The timeout of subscriptions in SECOND."
  600)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gen-adds [streams]
  (->> (range)
       (map (fn [x] {:type :invoke, :f :add, :value x, :stream (rand-nth streams)}))))
(defn gen-read [stream id]
  (gen/once {:type :invoke, :f :read, :value nil, :consumer-id id, :stream stream}))

(defrecord Client [opts test-streams subscription-results futures]
  client/Client

  (open! [this _ node]
    (dosync
     (let [service-url      (str node ":6570")
           client           (get-client service-url)]
       (-> this
           (assoc :client client)))))

  (setup! [this _]
    (info "-------- SETTING UP DONE ---------"))

  (invoke! [this _ op]
    (try+
     (case (:f op)
       :add (dosync
             (let [test-data {:key (:value op)}
                   producer  (create-producer (:client this) (:stream op))
                   write-future (write-data producer test-data)]
               (if (:async-write opts)
                 (alter futures assoc (:client this) write-future)
                 (.join write-future))
               (assoc op :type :ok)))
       :read (let [subscription-result (get subscription-results (:consumer-id op))]
                (try+ (subscribe (:client this)
                                 test-subscription-id
                                 (:stream op)
                                 :earliest
                                 subscription-timeout)
                      (catch Exception e nil))
                (consume (:client this)
                         test-subscription-id
                         (gen-collect-value-callback subscription-result))
                (Thread/sleep (* 1000 (:fetch-wait-time opts)))
                (assoc op
                       :type :ok
                       :value @subscription-result)))
     (catch java.net.SocketTimeoutException e
       (assoc op :type :fail :error :timeout))
     (catch Exception e
       (warn "---> Err when invoking an operation:" e)
       (assoc op :type :fail :error e))))

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
        ; "The stream of the only subscription."
        test-subscription-stream (rand-nth test-streams)
        subscription-results (into [] (repeatedly (:consumer-number opts) #(ref [])))
        futures (ref {})]

    (merge tests/noop-test
           opts
           {:pure-generators true
            :name    "HStream"
            :db      (common/db "0.6.0" test-streams)
            :client  (Client. opts test-streams subscription-results futures)
            :nemesis nemesis/noop
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
                        (gen/phases
                         (->> (gen-adds test-streams)
                              (gen/stagger (/ (:rate opts)))
                              (gen/time-limit (:write-time opts)))
                         (map #(gen-read test-subscription-stream %)
                              (range 0 (:consumer-number opts)))
                         (gen/sleep (+ 10 (:fetch-wait-time opts))))
                        (->> (->> [(gen/sleep 5)
                                   {:type :info :f :start}
                                   (gen/sleep 5)
                                   {:type :info :f :stop}]
                                  cycle)
                             (gen/time-limit (+ (:write-time opts)
                                                (:fetch-wait-time opts))))
                        )})))

(def cli-opts
  "Additional command line options."
  (concat common/cli-opts
          [["-c" "--consumer-number INT" "The number of clients that subscribes the same subscription."
            :default  10
            :parse-fn read-string
            :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
           ["-w" "--write-time SECOND" "The whole time to write data into database."
            :default  20
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
