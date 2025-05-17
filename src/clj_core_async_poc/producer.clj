(ns clj-core-async-poc.producer
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

(defonce ^{:private true :doc "Only create producer once"}
  producer nil)


(defn get-producer
  "Given server config, return kafka producer object"
  [servers]
  (when (nil? producer)
    (println "Producer is nil, creating connection")
    (alter-var-root #'producer (constantly (gregor/producer servers
                                                            {"value.serializer" "org.apache.kafka.common.serialization.StringSerializer"}))))
  producer)


(defn push-event
  "Push data to kafka using producer"
  [producer data]
  (gregor/send producer
               (:topic data)
               (:partition data)
               (:key data)
               (cc/generate-string (:value data)))
  (statsd/increment (format "%s.event-production.%s.success"
                            (get-in config [:metric-prefix :producer])
                            (:topic data))))


(defn start-producer
  "Run producer at specified RPM & for specified duration"
  [producer data rpm duration]
  (let [start-epoch (System/currentTimeMillis)
        rps (/ 60.0 rpm)
        sleep-ms (long (* rps 1000)) ;; convert to milliseconds
        end-time (+ (System/currentTimeMillis) (* duration 1000))
        event-counter (atom 0)]
    (loop [event-id 1]
      (let [diff (- end-time (System/currentTimeMillis))] ; end - curr
        (println (format "[%s] diff : %s , rps & sleep sec : %s"
                         (get-in data [:value :series])
                         diff
                         rps))
        (when (> diff 0)
          (println (format
                        "[%s] Sending event : # [%d] by [%s] in topic [%s] , partition [%s]"
                        (get-in data [:value :series])
                        event-id
                        (.getName (Thread/currentThread))
                        (:topic data)
                        (:partition data)))
              ;; add id to each message before producing
              (push-event producer (assoc-in data [:value :id] event-id))
              (Thread/sleep sleep-ms)
              (swap! event-counter inc)
              (recur (inc event-id)))))
    (println (format "[%s] Producer finished. Total-event : [%d]. Elapsed seconds : [%d]. Start epoch: %d, End epoch: %d"
                     (get-in data [:value :series])
                     @event-counter
                     (/ (- (System/currentTimeMillis) start-epoch) 1000.0)
                     start-epoch
                     (System/currentTimeMillis)))))


(defn main
  [series]
  (let [topic (get-in config
                      [:kafka :topic-names
                       (get-in config [:kafka :use_topic])])]
    (if (gregor/topic-exists? {:connection-string (get-in config
                                                          [:zookeeper :conn-string])}
                              topic)
      (do (statsd/setup (get-in config [:statsd :host])
                        (get-in config [:statsd :port]))
          (let [producer (get-producer (get-in config [:kafka :conn-string]))
                producer-thread (future (start-producer producer
                                                        {:topic topic
                                                         :partition (rand-int (get-in config
                                                                                      [:kafka :topic-paritions (get-in config [:kafka :use_topic])]))
                                                         :key nil
                                                         :value {:times (* 10000 10000) :num (* 1000 1000) :series series}}
                                                        (get-in config [:producer-config :messages-per-minute])
                                                        (get-in config [:producer-config :duration-seconds])))]
            (println (format "started producing msgs to %s" topic))))
      (do (println (format "Topic [%s] does not exist yet, pls explicitely create it before producing msg"
                           topic))
          (System/exit 0)))))


(comment
  ;; create topic before starting producer
  (gregor/create-topic {:connection-string (get-in config [:zookeeper :conn-string])} ;; zookeeper cordinates for creating topic
                       (get-in config
                               [:kafka :topic-names (get-in config [:kafka :use_topic])])
                       {:partitions (get-in config
                                            [:kafka :topic-paritions (get-in config [:kafka :use_topic])])
                        :replication-factor (get-in config
                                                    [:kafka :topic-replication (get-in config [:kafka :use_topic])])})

  ;; start production of msgs by invoking the main fn
  (main "series-1"))
