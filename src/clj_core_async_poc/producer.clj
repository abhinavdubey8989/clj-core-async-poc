(ns clj-core-async-poc.producer
  (:require [cheshire.core :as cc]
            [clj-statsd :as statsd]
            [gregor.core :as gregor]))


(defn get-producer
  "Given server config, return kafka producer object"
  [servers]
  (gregor/producer servers
                   {"value.serializer" "org.apache.kafka.common.serialization.StringSerializer"}))


(defn push-event
  "Push data to kafka using producer"
  [producer data]
  (gregor/send producer
               (:topic data)
               (:partition data)
               (:key data)
               (cc/generate-string (:value data)))
  (statsd/increment "event-production.success"))


(defn start-producer
  "Run producer at specified RPM & for specified duration"
  [producer data rpm duration]
  (let [start-epoch (System/currentTimeMillis)
        rps (/ 60 rpm) ;; Convert RPM to RPS
        end-time (+ (System/currentTimeMillis) (* duration 1000))]
    (loop [event-num 1]
      (when (< (System/currentTimeMillis) end-time)
        (println (format "Sending event : # %d by %s"
                         event-num
                         (.getName (Thread/currentThread))))

        ;; add id to each message before producing
        (push-event producer (assoc-in data
                                       [:value :id]
                                       event-num))
        (Thread/sleep (* rps 1000))
        (recur (inc event-num))))

    (println (format "Producer finished. Start epoch: %d, End epoch: %d"
                     start-epoch
                     (System/currentTimeMillis)))))


(comment

  ;; create-topic : run this only once
  (def partition-count 3)
  (def replication-factor 1)
  (gregor/create-topic {:connection-string "localhost:2181"} ;; zookeeper cordinates
                       "core_async_poc"
                       {:partitions partition-count
                        :replication-factor replication-factor})

  (def producer (get-producer "localhost:9092"))
  (def messages-per-minute 100) ;; produce 100 messages per minuites
  (def production-time-seconds (* 2 60)) ;; producer messages for 2 minutes

  ;; run producer at specified RPM & for specified duration
  (start-producer producer
                  {:topic "core_async_poc"
                   :partition (rand-int partition-count)
                   :key nil
                   :value {:times (* 10000 10000)
                           :num (* 1000 1000)}}
                  messages-per-minute
                  production-time-seconds)

  ;; produce 1 msg only
  (push-event producer
              {:topic "core_async_poc"
               :partition (rand-int partition-count)
               :key nil
               :value {:id 1212
                       :times (* 10000 10000)
                       :num (* 1000 1000)}}))

