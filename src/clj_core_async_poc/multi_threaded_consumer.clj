(ns clj-core-async-poc.multi-threaded-consumer
  (:require [cheshire.core :as cc]
            [clj-statsd :as statsd]
            [clojure.core.async :as async :refer [<!! >!!]]
            [gregor.core :as gregor]))


(defonce worker-channels (atom []))


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
    (statsd/increment "multi-threaded-consumer.event-consumption.success")))


(defn worker-index
  "Given the event, find the index of worker to put the event into
   Currently returns a random value [0, worker-channels)"
  [event]
  (rand-int (count @worker-channels)))


(defn pass-event-into-channel
  [event]
  (when (seq event)
    (let [parsed-event (cc/parse-string event true)]
      (>!! (->> parsed-event
                worker-index
                (nth @worker-channels))
           parsed-event))))


(defn start-workers
  "Create given number of channels & start the worker threads"
  [worker-count buffer-size]
  (dotimes [index worker-count]
    (let [channel (async/chan buffer-size)]
      (println (format "Starting worker thread : %s" (inc index)))
      (future
        (loop []
          (when-let [event (<!! channel)]
            (try
              (process-event event)
              (catch Exception e
                (println (format "Unexpected exception in start-workers : %s"
                                 (.getMessage e)))
                (println e)))
            (recur))))
      (swap! worker-channels conj channel))))


(defn start-consumer-polling
  "Start polling for kafka messages"
  [consumer worker-count buffer-size]
  (start-workers worker-count buffer-size)
  (loop []
    (let [records (gregor/poll consumer)]
      (doseq [{:keys [value] :as record} records]
        (pass-event-into-channel value))
      (recur))))


(comment
  (def consumer (get-consumer {:servers "localhost:9092"
                               :group-id "group-2"
                               :topics ["core_async_poc"]
                               :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}}))

  (def worker-count 10)
  (def buffer-size 100)

  ;; Run consumer on a separate thread so that it doesn't block the REPL
  (def consumer-thread (future (start-consumer-polling consumer
                                                       worker-count
                                                       buffer-size))))
