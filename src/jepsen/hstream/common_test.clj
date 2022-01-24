(ns jepsen.hstream.common-test
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
            [clojure.pprint :as pp]
            [jepsen [db :as db] [cli :as cli] [checker :as checker]
             [client :as client] [generator :as gen] [nemesis :as nemesis]
             [tests :as tests]]
            [slingshot.slingshot :refer [try+]]
            [jepsen.hstream.client :refer :all]
            [jepsen.hstream.utils :refer :all]
            [jepsen.hstream.checker :as local-checker]
            [jepsen.hstream.mvar :refer :all]
            [clojure.stacktrace :refer [e]])
  (:import [io.hstream HRecord]))

(defn db
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
      (teardown! [_ test node]
        (info node ">>> Tearing down DB: HStream" version)
        ;; SKIP CLEANING STREAMS
        ;;(when (= node (first (:nodes test)))
        ;;  (let [service-url (str node ":6570")
        ;;        client (get-client service-url)]
        ;;    (dorun
        ;;     (map #(try+ (delete-stream client %)
        ;;                 (catch Exception e nil)) streams))))
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def gen-adds
  (->> (range)
       (map (fn [x] {:type :invoke, :f :add, :value x}))))
(defn gen-read
  [id]
  (gen/once {:type :invoke, :f :read, :value nil, :consumer-id id}))
(defn gen-sub
  [stream]
  (gen/once {:type :invoke, :f :sub, :value nil, :stream stream}))
(defn gen-add [x] (gen/once {:type :invoke, :f :add, :value x}))

(defrecord Client [opts]
  client/Client
    (open! [this _ node]
      (let [service-url (str node ":6570")
            client (get-client service-url)]
        (assoc this :client client)))
    (setup! [this _] (info "-------- SETTING UP DONE ---------"))
    (invoke! [this _ op])
    (teardown! [this _])
    (close! [this _] (.close (:client this))))

(defn hstream-test
  "Given an options map from the command-line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:pure-generators true,
          :name "HStream",
          :db (db "0.6.0" [] (ref {})),
          :client (Client. opts),
          :nemesis nemesis/noop,
          :ssh {:dummy? (:dummy opts)}}))

(def cli-opts
  "Additional command line options."
  [["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default 10 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-f" "--fetch-wait-time SECOND"
    "The time between starting fetching from the stream and shutting down it."
    :default 15 :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-s" "--max-streams INT"
    "The number of HStream streams to be written to in the test." :default 1
    :parse-fn read-string :validate
    [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--dummy BOOL" "Whether to use dummy ssh connection for local test."
    :default false :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean"]]
   [nil "--async-write BOOL"
    "Whether to use async write mode. If it is set to false, return :ok until every write operation finishes."
    :default false :parse-fn read-string :validate
    [#(boolean? %) "Must be a boolean"]]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn hstream-test,
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
