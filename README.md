# clj-async-core-poc

This is a PoC to see the impact on both : throughput & total processing time by leveraging Clojure's `core.async` library to scale a Kafka consumer.

# Commands
- create project  : `lein new app clj-core-async-poc`
- install dependency  : `lein -U deps`

- Follow any one of the below  to start
- start the project repl headless & connect : `lein repl :headless`  & `lein repl :connect localhost:<port>` , or
- run : `lein repl`


# Switch ns
`Start single-th consumer`
(do (require '[clj-core-async-poc.single-threaded-consumer])
    (in-ns 'clj-core-async-poc.single-threaded-consumer)
    (main))


`Start multi-th consumer`
(do (require '[clj-core-async-poc.multi-threaded-consumer])
    (in-ns 'clj-core-async-poc.multi-threaded-consumer)
    (main))


`Start producing msgs`

(do (require '[clj-core-async-poc.producer])
    (in-ns 'clj-core-async-poc.producer)
    (main "producer-single-th-series-1"))
    
(do (require '[clj-core-async-poc.producer])
    (in-ns 'clj-core-async-poc.producer)
    (main "producer-multi-th-series-1"))


# [Docker] Inspect Kafka topic : start console consumer
sudo docker exec -it <container-name> /bin/bash -c "/use/bin/kafka-console-consumer --bootstrap-server localhost:9092 --topic <topic-name>"
sudo docker exec -it my_kafka_1 /bin/bash -c "/usr/bin/kafka-console-consumer --bootstrap-server localhost:9092 --topic core_async_poc_single_th_1"

# [Docker] List Kafka topics
sudo docker exec -it my_kafka_1 /bin/bash -c "/usr/bin/kafka-topics --bootstrap-server localhost:9092 --list"

# [Docker] Describe Kafka topic
sudo docker exec -it my_kafka_1 /bin/bash -c "/usr/bin/kafka-topics --bootstrap-server localhost:9092 --describe --topic core_async_poc_single_th_1"

# [Docker] List consumer groups
sudo docker exec -it my_kafka_1 /bin/bash -c "/usr/bin/kafka-consumer-groups --bootstrap-server localhost:9092 --list"

# [Docker] Check kafka container logs
sudo docker logs -f my_kafka_1

# [Clj] Check topic exists using gregor lib
(gregor/topic-exists? {:connection-string "mtr_host:2181"} "core_async_poc_single_th_1")
