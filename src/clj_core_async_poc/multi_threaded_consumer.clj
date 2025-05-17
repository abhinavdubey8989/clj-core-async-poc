(ns clj-core-async-poc.multi-threaded-consumer
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as cc]
            [clj-statsd :as statsd]
            [clojure.core.async :as async :refer [<!! >!!]]
            [gregor.core :as gregor]))



(def config
  (-> "config.edn"
      io/resource
      slurp
      edn/read-string))


(defonce worker-channels (atom []))

(defonce ^{:private true :doc "Create consumer only once"}
  consumer nil)


(defn get-consumer
  "Return kafka consumer object"
  [config]
  (when (nil? consumer)
    (println "Multi-threaded consumer is nil, creating connection")
    (alter-var-root #'consumer (constantly (gregor/consumer (:servers config)
                                                            (:group-id config)
                                                            (:topics config)
                                                            (:config config)))))
  consumer)


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
    (statsd/increment (format "%s.event-consumption.success"
                              (get-in config [:metric-prefix :multi-threaded])))))


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


(defn main
  []
  (let [topic (get-in config [:kafka :topic-names :multi-threaded])]
    (if (gregor/topic-exists? {:connection-string (get-in config
                                                          [:zookeeper :conn-string])}
                              topic)
      (do (statsd/setup (get-in config [:statsd :host])
                        (get-in config [:statsd :port]))
          (let [consumer (get-consumer {:servers (get-in config [:kafka :conn-string])
                                        :group-id (get-in config [:kafka :consumer-group-id :multi-threaded])
                                        :topics [topic]
                                        :config {"value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"}})
                ;; Run consumer on a separate thread so that it doesn't block the REPL
                consumer-thread (future (start-consumer-polling consumer
                                                                (get-in config [:worker-config :count])
                                                                (get-in config [:worker-config :buffer-size])))]
            (print (format "started multi-threaded consumer using topic %s ... " topic))))
      (do (println (format
                    "Topic [%s] does not exist yet, pls explicitely create it before starting multi-threaded consumer"
                    topic))
          (System/exit 0)))))

(comment
  ;; start consumption of msgs by invoking the main fn
  (main))
