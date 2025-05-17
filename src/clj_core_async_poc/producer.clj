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
        rps         (/ 60 rpm) ;; Convert RPM to RPS
        end-time    (+ (System/currentTimeMillis) (* duration 1000))]
    (loop [event-num 1]
      (let [curr-epoc (System/currentTimeMillis)
            diff (- end-time curr-epoc)
            sleep (* rps 1000)]
        (println (format "diff : %s , rps : %s , sleep for sec : %s , curr-epoc : %s"
                         diff
                         rps
                         sleep
                         curr-epoc))
        (when (< curr-epoc end-time)
          (println (format "Sending event : # [%d] by [%s] in topic [%s] , partition [%s]"
                           event-num
                           (.getName (Thread/currentThread))
                           (:topic data)
                           (:partition data)))
          ;; add id to each message before producing
          (push-event producer (assoc-in data [:value :id] event-num))
          (Thread/sleep sleep)
          (recur (inc event-num)))))
    (println (format "Producer finished. Start epoch: %d, End epoch: %d"
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
