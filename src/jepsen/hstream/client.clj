(ns jepsen.hstream.client
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.core.reducers :as reducers]
            [clojure.tools.logging :refer :all]
            [jepsen.hstream.utils :refer :all])
  (:import [io.hstream HStreamClient HStreamClientBuilder ProducerBuilder
            HRecord HRecordBuilder Subscription SubscriptionOffset
            SubscriptionOffset$SpecialOffset RecordId HRecordReceiver
            ReceivedHRecord Responder Stream]
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

(defn write-data
  [producer data-to-write]
  "data-to-write is a map: {key1 value1 key2 value2 ...}
  returns: record-id"
  (let [builder (HRecord/newBuilder)
        reducef (fn [acc k v] (HRecordBuilder/.put acc (str k) (str v)))
        final-builder (reducers/reduce reducef builder data-to-write)
        hrecord (.build final-builder)]
    (.write producer hrecord)))

(defn subscribe
  [client sub-id stream offset timeout]
  "offset can be one of :latest :earliest and {:batch-id n1 :batch-index n2}"
  (let [real-offset (case offset
                      :earliest (SubscriptionOffset.
                                  (SubscriptionOffset$SpecialOffset/EARLIEST))
                      :latest (SubscriptionOffset.
                                (SubscriptionOffset$SpecialOffset/LATEST))
                      :else (RecordId. (:batch-id offset)
                                       (:batch-index offset)))
        subscription
          (.build
            (.ackTimeoutSeconds
              (.offset (.stream (.subscription (Subscription/newBuilder) sub-id)
                                stream)
                       real-offset)
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
