(ns clj-core-async-poc.single-threaded-consumer
  (:require [cheshire.core :as cc]
            [clj-statsd :as statsd]
            [gregor.core :as gregor]))


(defn get-consumer
  "Return kafka consumer object"
  [config]
  (gregor/consumer (:servers config)
                   (:group-id config)
                   (:topics config)
                   (:config config)))


(defn process-event
  "Business logic to be executed for each event"
  [event]
  (let [start-time (System/currentTimeMillis)
        event (cc/parse-string event true)]

    ;; simulate work : find square-root of a number repeatedly
    (dotimes [_ (:times event)]
      (Math/sqrt (:num event)))

    ;; logging
    (let [end-time (System/currentTimeMillis)
          elapsed-seconds (/ (- end-time start-time) 1000.0)]
      (println {:id (:id event)
                :thread (.getName (Thread/currentThread))
                :duration elapsed-seconds}))

    ;; increment metric
    (statsd/increment "single-threaded-consumer.event-consumption.success")))


(defn start-consumer-polling
  "Start polling for kafka messages"
  [consumer]
  (loop []
    (let [records (gregor/poll consumer)]
      (doseq [{:keys [value] :as record} records]
        (process-event value))
      (recur))))


(comment
  (def consumer (get-consumer {:servers "localhost:9092"
                               :group-id "group-1"
                               :topics ["core_async_poc"]
                               :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}}))

  ;; Run consumer on a separate thread so that it doesn't block the REPL
  (def consumer-thread (future (start-consumer-polling consumer))))
