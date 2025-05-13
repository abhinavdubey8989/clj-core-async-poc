(ns clj-core-async-poc.multi-threaded-consumer
  (:require [gregor.core :as gregor]
            [clj-statsd :as statsd]
            [clojure.core.async :as async :refer [<!! >!!]]))


(defonce worker-channels (atom []))


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


(defn worker-index
  [event]
  (if (seq (:id event))
    (mod (hash (:id event)) (count @worker-channels))
    (rand-int (count @worker-channels))))


(defn pass-event-into-channel
  [event]
  (when (seq event)
    (>!! (->> event
              worker-index
              (nth @worker-channels))
         event)))


(defn start-workers
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
  [consumer worker-count buffer-size]
  (start-workers worker-count buffer-size)
  (loop []
    (let [records (gregor/poll consumer)]
      (doseq [{:keys [value] :as record} records]
        (pass-event-into-channel value))
      (recur))))


(comment
  (def consumer (get-consumer {:servers "localhost:9092"
                               :group-id "group-1"
                               :topics ["core_async_poc"]
                               :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}}))

  (def worker-count 10)
  (def buffer-size 100)

  ;; Run consumer on a separate thread so that it doesn't block the REPL
  (def consumer-thread (future (start-consumer-polling consumer
                                                       worker-count
                                                       buffer-size))))
