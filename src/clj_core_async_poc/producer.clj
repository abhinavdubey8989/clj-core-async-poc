(ns clj-core-async-poc.producer
  (:require [gregor.core :as gregor]
            [clj-statsd :as statsd]))


(defn get-producer
  [servers]
  (gregor/producer servers
                   {"value.serializer" "org.apache.kafka.common.serialization.StringSerializer"}))


(defn push-event
  [producer data]
  (gregor/send producer
               (:topic data)
               (:partition data)
               (:key data)
               (.toString (:value data)))
  (statsd/increment "event-production.success"))


(defn start-producer
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
  (def producer (get-producer "localhost:9092"))
  (def messages-per-minute 100) ;; produce 100 messages per minuites
  (def production-time-seconds (* 2 60)) ;; producer messages for 2 minutes

  (start-producer producer
                  {:topic "core_async_poc"
                   :partition 0
                   :key nil
                   :value {:times (* 10000 10000)
                           :num (* 1000 1000)}}
                  messages-per-minute
                  production-time-seconds))

