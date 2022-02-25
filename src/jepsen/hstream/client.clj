(ns jepsen.hstream.client
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
            [jepsen.hstream.utils :refer :all])
  (:import [io.hstream HStreamClient HStreamClientBuilder ProducerBuilder
            HRecord HRecordBuilder Subscription RecordId HRecordReceiver
            ReceivedHRecord Responder Stream Record]
           [io.hstream.impl HStreamClientBuilderImpl]
           [java.util.concurrent TimeUnit]))

(defn get-client
  [url]
  (.build (HStreamClientBuilder/.serviceUrl (HStreamClient/builder) url)))

(defn get-client-until-ok
  [url]
  (try (get-client url)
       (catch Exception e (do (Thread/sleep 1000) (get-client-until-ok url)))))

(defn create-stream
  [client stream-name]
  (HStreamClient/.createStream client stream-name))

(defn list-streams
  [client]
  (map #(Stream/.getStreamName %) (.listStreams client)))

(defn delete-stream
  [client stream-name]
  (try (HStreamClient/.deleteStream client stream-name)
       (catch Exception e nil)))

(defn delete-all-streams
  [client]
  (let [all-streams (list-streams client)]
    (dorun (map #(delete-stream client %) all-streams))))

(defn create-producer
  [client stream-name]
  (.build (ProducerBuilder/.stream (HStreamClient/.newProducer client)
                                   stream-name)))

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
         record (.build (.hRecord (.orderingKey (Record/newBuilder) key)
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

(defn unsubscribe [client sub-id] (.deleteSubscription client sub-id))

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
