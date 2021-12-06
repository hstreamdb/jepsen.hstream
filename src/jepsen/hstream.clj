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
            [clojure.stacktrace :refer [e]])
  (:import [io.hstream HRecord]))

(defn db
  "HStream DB for a particular version. Note that the node
   environment is set by docker-compose so we do not need
   to do anything here."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node ">>> Setting up DB: HStream" version))

    (teardown! [_ test node]
      (info node ">>> Tearing down DB: HStream" version))))

;;;;;;;;;; Global Variables ;;;;;;;;;;

;; Streams

(def test-stream-name-length
  "The length of random stream name. It should be a positive natural
   and not too small to avoid name confliction."
  20)

;; Write: Producers & Related Data
(def test-producers
  "Producers for clients to write data to a stream. It is a map whose key
   is the stream name and the value is the producer. The map is initialized
   during the `setup!` process."
  (ref {}))

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

(def gen-adds (->> (range)
                   (map (fn [x] {:type :invoke, :f :add,  :value x}))))
(def gen-read (gen/once {:type :invoke, :f :read, :value nil}))

(defrecord Client [opts test-streams test-subscription-stream]
  client/Client

  (open! [this _ node]
    (dosync
     (let [service-url      (str node ":6570")
           client           (get-client service-url)
           test-stream-name (rand-nth test-streams)]
       (-> this
           (assoc :client client)
           (assoc :stream test-stream-name)))))

  (setup! [this _]
    (dosync
     (dorun
      (map #(let [_        (try+ (create-stream   (:client this) %)
                                 (catch Exception e nil))
                  producer (try+ (create-producer (:client this) %)
                                 (catch Exception e nil))]
              (alter test-producers assoc % producer)) test-streams)))
    (info "-------- SETTING UP DONE ---------"))

  (invoke! [this _ op]
    (try+
     (case (:f op)
       :add (dosync
             (let [test-data {:key (:value op)}
                   producer  (get @test-producers (:stream this))]
               (.get (write-data producer test-data))
               (assoc op :type :ok :stream (:stream this))))
       :read (dosync
              (let [subscription-results (atom [])]
                (try+ (subscribe (:client this)
                                 test-subscription-id
                                 test-subscription-stream
                                 :earliest
                                 subscription-timeout)
                      (catch Exception e nil))
                (consume (:client this)
                         test-subscription-id
                         (gen-collect-value-callback subscription-results))
                (Thread/sleep (* 1000 (:fetch-wait-time opts)))
                (assoc op
                       :type :ok
                       :value @subscription-results
                       :stream test-subscription-stream))))
     (catch java.net.SocketTimeoutException e
       (assoc op :type :fail :error :timeout))
     (catch Exception e
       (warn "---> Err when invoking an operation:" e)
       (assoc op :type :fail :error e))))

  (teardown! [this _]
    (try+ (dorun (map #(delete-stream (:client this) %) test-streams))
          (catch Exception e nil)))

  (close! [this _]
    (try+
     (dosync (println ">>> Closing client...")
             (.close (:client this)))
     (catch Exception e nil))))

(defn hstream-test
  "Given an options map from the command-line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [; "The random-named stream names. It is a vector of strings."
        test-streams (into [] (repeatedly (:max-streams opts)
                                          #(rs/string test-stream-name-length)))
        ; "The stream of the only subscription."
        test-subscription-stream (rand-nth test-streams)]

    (merge tests/noop-test
           opts
           {:pure-generators true
            :name    "HStream"
            :os      ubuntu/os
            :db      (db "0.6.0")
            :client  (Client. opts test-streams test-subscription-stream)
            :nemesis nemesis/noop ;(nemesis/clock-scrambler 86400);(nemesis/hammer-time "hstream-server")
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
                         (->> gen-adds
                              (gen/stagger (/ (:rate opts)))
                              (gen/time-limit (:write-time opts)))
                         (gen/repeat (:consumer-number opts)
                                     (gen/clients gen-read))
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
  [["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default  10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-c" "--consumer-number INT" "The number of clients that subscribes the same subscription."
    :default  10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-s" "--max-streams INT" "The number of HStream streams to be written to in the test."
    :default  1
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-f" "--fetch-wait-time SECOND" "The time between starting fetching from the stream and shutting down it."
    :default  15
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-w" "--write-time SECOND" "The whole time to write data into database."
    :default  20
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn hstream-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
