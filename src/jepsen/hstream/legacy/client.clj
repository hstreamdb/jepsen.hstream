(ns jepsen.hstream.legacy.client
  (:require [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
            [slingshot.slingshot :refer [try+]]
            [jepsen.hstream.common.utils :refer :all])
  (:import [io.hstream HStreamClient HStreamClientBuilder ProducerBuilder
            HRecord HRecordBuilder Subscription HRecordReceiver Stream Record]))

(defn get-client
  ([url]
   (.build (.requestTimeoutMs
             (HStreamClientBuilder/.serviceUrl (HStreamClient/builder) url)
             5000)))
  ([url timeout]
   (.build (.requestTimeoutMs
             (HStreamClientBuilder/.serviceUrl (HStreamClient/builder) url)
             timeout))))

(defn get-client-until-ok
  ([url]
   (try+ (get-client url)
         (catch Object _
           (do (Thread/sleep 1000) (get-client-until-ok url)))))
  ([url timeout]
   (try+ (get-client url timeout)
         (catch Object _
           (do (Thread/sleep 1000) (get-client-until-ok url timeout))))))

(defn get-client-among-urls
  ([urls]
   (reduce (fn [acc url]
             (let [[_ target-client] acc]
               (if (nil? target-client)
                 (try+ (let [client (get-client url)] [url client])
                       (catch Object _
                         (do (info url "seems unavailable. Do not worry, trying another one...")
                             [url nil])))
                 acc)))
     [(first urls) nil]
     urls))
  ([urls timeout]
   (reduce (fn [acc url]
             (let [[_ target-client] acc]
               (if (nil? target-client)
                 (try+ (let [client (get-client url timeout)] [url client])
                       (catch Object _
                         (do (info url "seems unavailable. Do not worry, trying another one...")
                             [url nil])))
                 acc)))
     [(first urls) nil]
     urls)))

(defn get-client-start-from-url
  ([url]
   (let [all-urls (map #(str "hstream://" % ":6570") ["n1" "n2" "n3" "n4" "n5"])
         other-urls (remove #(= % url) all-urls)]
     (get-client-among-urls (cons url other-urls))))
  ([url timeout]
   (let [all-urls (map #(str "hstream://" % ":6570") ["n1" "n2" "n3" "n4" "n5"])
         other-urls (remove #(= % url) all-urls)]
     (get-client-among-urls (cons url other-urls) timeout))))

(defn create-stream
  ([client stream-name] (HStreamClient/.createStream client stream-name))
  ([client stream-name partitions]
   (let [replication 1]
     (HStreamClient/.createStream client stream-name replication partitions))))

(defn list-streams
  [client]
  (map #(Stream/.getStreamName %) (.listStreams client)))

(defn delete-stream
  ([client stream-name] (HStreamClient/.deleteStream client stream-name))
  ([client stream-name force]
   (HStreamClient/.deleteStream client stream-name force)))

(defn delete-all-streams
  ([client]
   (let [all-streams (list-streams client)]
     (dorun (map #(delete-stream client %) all-streams))))
  ([client force]
   (let [all-streams (list-streams client)]
     (dorun (map #(delete-stream client % force) all-streams)))))

(defn create-producer
  ([client stream-name]
   (.build (ProducerBuilder/.stream (HStreamClient/.newProducer client)
                                    stream-name)))
  ([client stream-name timeout]
   (.build (ProducerBuilder/.requestTimeoutMs
             (ProducerBuilder/.stream (HStreamClient/.newProducer client)
                                      stream-name)
             timeout))))

(defn map-to-hrecord
  "data-to-write is a map: {key1 value1 key2 value2 ...}
   returns: HRecord"
  [data-to-write]
  (let [hrecord-builder (HRecord/newBuilder)
        reducef (fn [acc k v] (HRecordBuilder/.put acc (str k) (str v)))
        final-builder (reducers/reduce reducef hrecord-builder data-to-write)]
    (.build final-builder)))

(defn write-data
  "data-to-write is a map: {key1 value1 key2 value2 ...}
  returns: record-id"
  ([producer data-to-write]
   (let [hrecord (map-to-hrecord data-to-write)
         record (.build (.hRecord (Record/newBuilder) hrecord))]
     (.write producer record)))
  ([producer data-to-write key]
   (let [hrecord (map-to-hrecord data-to-write)
         record (.build (.hRecord (.partitionKey (Record/newBuilder) key)
                                  hrecord))]
     (.write producer record))))

(defn subscribe
  [client sub-id stream timeout]
  (let [subscription (.build (.ackTimeoutSeconds
                               (.stream (.subscription (Subscription/newBuilder)
                                                       sub-id)
                                        stream)
                               timeout))]
    (.createSubscription client subscription)))

(defn unsubscribe
  ([client sub-id] (.deleteSubscription client sub-id))
  ([client sub-id force] (.deleteSubscription client sub-id force)))

(defn consume
  [client sub-id callback]
  (let [hrecord-receiver (reify
                           HRecordReceiver
                             (processHRecord [this received-hrecord responder]
                               (callback received-hrecord responder)))
        consumer (.build (.hRecordReceiver (.subscription (.newConsumer client)
                                                          sub-id)
                                           hrecord-receiver))]
    (.awaitRunning (.startAsync consumer))
    consumer))

(defn example-hrecord-callback
  [received-hrecord responder]
  (let [record-id (.getRecordId received-hrecord)
        hrecord (.getHRecord received-hrecord)]
    (info "~~~ Received: ID = " record-id " contents = " hrecord)
    (.ack responder)))

(defn gen-collect-hrecord-callback
  [ref]
  (fn [received-hrecord responder]
    (dosync (let [record-id (.getRecordId received-hrecord)
                  hrecord (.getHRecord received-hrecord)]
              (alter ref conj {:record-id record-id, :hrecord hrecord})
              (.ack responder)))))

(defn gen-collect-value-callback
  [ref]
  (fn [received-hrecord responder]
    (let [record-id (.getRecordId received-hrecord)
          hrecord (.getHRecord received-hrecord)
          value (parse-int (.getString hrecord ":key"))]
      (dosync
        (alter ref conj value)
        (if (in? @ref value)
          (.ack responder)
          (warn
            "Client got an message, but failed to acturally RECEIVING it!"))))))
