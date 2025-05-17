(ns clj-core-async-poc.single-threaded-consumer
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as cc]
            [clj-statsd :as statsd]
            [gregor.core :as gregor]))


(def config
  (-> "config.edn"
      io/resource
      slurp
      edn/read-string))


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
        event      (cc/parse-string event true)]
    
      ;; simulate work : find square-root of a number repeatedly
    (dotimes [_ (:times event)]
      (Math/sqrt (:num event)))
    
      ;; logging
    (let [end-time        (System/currentTimeMillis)
          elapsed-seconds (/ (- end-time start-time) 1000.0)]
      (println {:id       (:id event)
                :thread   (.getName (Thread/currentThread))
                :duration elapsed-seconds}))
    
      ;; increment metric
    (statsd/increment (format "%s.event-consumption.success"
                              (get-in config [:metric-prefix :single-threaded])))))


(defn start-consumer-polling
  "Start polling for kafka messages"
  [consumer]
  (println "single-th consumer , invoking start-consumer-polling")
  (loop []
    (let [records (gregor/poll consumer)]
      (doseq [{:keys [value]
               :as   record} records]
        (println "single-th consumer , invoking process-event")
        (process-event value))
      (recur))))


(defn main
  []
  (statsd/setup (get-in config [:statsd :host])
                (get-in config [:statsd :port]))
  (let [topic (get-in config [:kafka :topic-names :single-threaded])
        consumer (get-consumer {:servers (get-in config [:kafka :conn-string])
                                :group-id (get-in config [:kafka :consumer-group-id :single-threaded])
                                :topics [topic]
                                :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}})
        ;; Run consumer on a separate thread so that it doesn't block the REPL
        consumer-thread (future (start-consumer-polling consumer))]
    (print (format "started single threaded consumer using topic %s ... "
                   topic))))


(comment
  ;; start consumption of msgs by invoking the main fn
  (def consumer (get-consumer {:servers "65.0.4.68:9092"
                               :group-id "group-1"
                               :topics ["core_async_poc_single_th_1"]
                               :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}}))
  
  (main)
  )
