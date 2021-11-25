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
            [jepsen.hstream.mvar :refer :all])
  (:import [io.hstream HRecord]))

(defn db
  "HStream DB for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node ">>> Setting up DB: HStream" version))

    (teardown! [_ test node]
      (info node ">>> Tearing down DB: HStream" version))))

;;;;;;;;;; Global Variables ;;;;;;;;;;

;; Streams
(def max-streams
  "The number of HStream streams. It should be a positive natural.
   The streams will be named by random alphabets."
  1)
(def test-stream-name-length
  "The length of random stream name. It should be a positive natural
   and not too small to avoid name confliction."
  20)
(def test-streams
  "The random-named stream names. It is a vector of strings."
  (into [] (repeatedly max-streams #(rs/string test-stream-name-length))))

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

(def test-subscription-stream
  "The stream of the only subscription."
  (rand-nth test-streams))

(def subscription-timeout
  "The timeout of subscriptions in SECOND."
  600)

(def subscription-fetch-sleep-time
  "The sleep time between starting fetching from the stream and
   shutting down it. It is an integer in SECOND."
  15)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def gen-adds (->> (range)
                   (map (fn [x] {:type :invoke, :f :add,  :value x}))))
(def gen-read (gen/once {:type :invoke, :f :read, :value nil}))

(defrecord Client [conn]
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
      (map #(let [_ (try+ (create-stream (:client this) %) (catch Exception e nil))
                  producer (try+ (create-producer (:client this) %) (catch Exception e nil))]
              (alter test-producers assoc % producer)) test-streams)))
    (info "-------- SETTING UP DONE ---------"))

  (invoke! [this _ op]
    (try+
     (case (:f op)
       :add (dosync
             (let [test-data       {:key (:value op)}
                   producer        (get @test-producers (:stream this))]
               (write-data producer test-data)
               (assoc op :type :ok :stream (:stream this))))
       :read (dosync
              (let [subscription-results (atom [])]
                (try+ (subscribe (:client this) test-subscription-id test-subscription-stream :earliest subscription-timeout) (catch Exception e nil))
                (try+ (consume (:client this) test-subscription-id (gen-collect-value-callback subscription-results)) (catch Exception e nil))
                (Thread/sleep (* 1000 subscription-fetch-sleep-time))
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
    (try+ (dorun (map #(delete-stream (:client this) %) test-streams)) (catch Exception e nil)))

  (close! [this _]
    (try+
     (dosync (println ">>> Closing client...")
             (.close (:client this)))
     (catch Exception e nil))))

(defn hstream-test
  "Given an options map from the command-line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (info "Creating test..." opts)
  (merge tests/noop-test
         opts
         {:pure-generators true
          :name    "HStream"
          :os      ubuntu/os
          :db      (db "0.6.0")
          :client  (Client. nil)
          :checker (checker/compose
                     {:source-set (local-checker/set+)})
          :nemesis (nemesis/partition-random-halves)
          :generator (gen/phases
                      (->> gen-adds
                           (gen/stagger (/ (:rate opts)))
                           (gen/nemesis
                            (cycle [(gen/sleep 5)
                                    {:type :info, :f :start}
                                    (gen/sleep 5)
                                    {:type :info, :f :stop}]))
                           (gen/time-limit (:time-limit opts)))
                      (gen/log "Healing cluster")
                      (gen/nemesis (gen/once {:type :info, :f :stop}))
                      (gen/log "Waiting for recovery")
                      (gen/repeat (:subscription-client-number opts)
                       (gen/clients gen-read))
                      (gen/sleep (* 2 subscription-fetch-sleep-time)))}))

(def cli-opts
  "Additional command line options."
  [["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default  10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-c" "--subscription-client-number INT" "The number of clients that subscribes the same subscription."
    :default  10
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
