(ns clj-core-async-poc.producer
  (:require [gregor.core :as gregor]))

(defn get-producer
  [servers]
  (gregor/producer servers))


(defn push-event
  [producer data]
  (gregor/send producer
               (:topic data)
               (:partition data)
               (:key data)
               (:value data)))


(comment
  (def producer (get-producer "localhost:9092"))

  (push-event producer
              {:topic "test-topic"
               :partition 0
               :key nil
               :value "{\"foo\":42}"})

  (print "123"))
