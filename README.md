akka-streams-kafka
==================

Using akka-streams to consume a Kafka queue

Start the server
================
> bin/zookeeper-server-start.sh config/zookeeper.properties
> bin/kafka-server-start.sh config/server.properties

Create a topic
==============
> bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic item-topic

Send messages
=============
> bin/kafka-console-producer.sh --broker-list localhost:9092 --topic item-topic
{"id": "1", "title": "Item", "price": "9900"}

Start Akka application
======================
> sbt run
select [3] com.xebia.kafka.AkkaKafkaStream
