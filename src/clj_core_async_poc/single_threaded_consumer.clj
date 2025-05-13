(ns clj-core-async-poc.single-threaded-consumer
  (:require [gregor.core :as gregor]
            [clj-statsd :as statsd]))


(defn get-consumer
  [config]
  (gregor/consumer (:servers config)
                   (:group-id config)
                   (:topics config)
                   (:config config)))


(defn process-event
  [event]
  (let [start-time (System/currentTimeMillis)]

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
    (statsd/increment "event-consumption.success")))


(defn start-consumer-polling
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
