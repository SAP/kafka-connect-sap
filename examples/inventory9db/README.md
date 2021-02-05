### Example inventory9db: kafka-hana-connect using debezium record state extraction from Cassandra to HANA

This example uses Debezium Cassandra connector and HANA connector to copy tables from Cassandra to HANA using table change events or CDC events. This example is similar to `inventory7db` and `inventory8db` which use MySQL and Posgres instead, respectively. However, Debezium Cassandra connector runs part of Cassandra and not in Kafka Connect.

For further information on Debezium and its Cassandra connector, refer to [Debezium documentation](https://debezium.io/documentation/reference/index.html).

NOTE: This example is not working and results in the below error.
```
2021-02-05 21:16:39,672 ERROR  ||  WorkerSinkTask{id=inventory9-hana-sink-0} Task threw an uncaught and unrecoverable exception. Task is being killed and will not recover until manually restarted. Error: Unsupported Avro type STRUCT for name source   [org.apache.kafka.connect.runtime.WorkerSinkTask]
java.lang.RuntimeException: Unsupported Avro type STRUCT
	at scala.sys.package$.error(package.scala:30)
	at com.sap.kafka.utils.GenericJdbcTypeConverter.convertToDBType(GenericJdbcTypeConverter.scala:39)
	at com.sap.kafka.utils.GenericJdbcTypeConverter.convertToDBType$(GenericJdbcTypeConverter.scala:19)
	at com.sap.kafka.utils.hana.HANAJdbcTypeConverter$.convertToHANAType(HANAJdbcTypeConverter.scala:16)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.$anonfun$add$4(HANASinkRecordsCollector.scala:130)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.$anonfun$add$4$adapted(HANASinkRecordsCollector.scala:126)
	at scala.collection.Iterator.foreach(Iterator.scala:943)
	at scala.collection.Iterator.foreach$(Iterator.scala:943)
	at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
	at scala.collection.IterableLike.foreach(IterableLike.scala:74)
	at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
	at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.add(HANASinkRecordsCollector.scala:126)
	at com.sap.kafka.connect.sink.hana.HANAWriter.$anonfun$write$2(HANAWriter.scala:56)
	at com.sap.kafka.connect.sink.hana.HANAWriter.$anonfun$write$2$adapted(HANAWriter.scala:44)
	at scala.collection.TraversableLike$WithFilter.$anonfun$foreach$1(TraversableLike.scala:912)
	at scala.collection.Iterator.foreach(Iterator.scala:943)
	at scala.collection.Iterator.foreach$(Iterator.scala:943)
	at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
	at scala.collection.IterableLike.foreach(IterableLike.scala:74)
	at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
	at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
	at scala.collection.TraversableLike$WithFilter.foreach(TraversableLike.scala:911)
	at com.sap.kafka.connect.sink.hana.HANAWriter.write(HANAWriter.scala:44)
	at com.sap.kafka.connect.sink.GenericSinkTask.put(GenericSinkTask.scala:36)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.deliverMessages(WorkerSinkTask.java:563)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.poll(WorkerSinkTask.java:326)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.iteration(WorkerSinkTask.java:229)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.execute(WorkerSinkTask.java:201)
	at org.apache.kafka.connect.runtime.WorkerTask.doRun(WorkerTask.java:185)
	at org.apache.kafka.connect.runtime.WorkerTask.run(WorkerTask.java:235)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:834)
2021-02-05 21:16:39,677 ERROR  ||  WorkerSinkTask{id=inventory9-hana-sink-0} Task threw an uncaught and unrecoverable exception   [org.apache.kafka.connect.runtime.WorkerTask]
org.apache.kafka.connect.errors.ConnectException: Exiting WorkerSinkTask due to unrecoverable exception.
	at org.apache.kafka.connect.runtime.WorkerSinkTask.deliverMessages(WorkerSinkTask.java:591)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.poll(WorkerSinkTask.java:326)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.iteration(WorkerSinkTask.java:229)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.execute(WorkerSinkTask.java:201)
	at org.apache.kafka.connect.runtime.WorkerTask.doRun(WorkerTask.java:185)
	at org.apache.kafka.connect.runtime.WorkerTask.run(WorkerTask.java:235)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:834)
Caused by: java.lang.RuntimeException: Unsupported Avro type STRUCT for name source
	at scala.sys.package$.error(package.scala:30)
	at com.sap.kafka.utils.GenericJdbcTypeConverter.convertToDBType(GenericJdbcTypeConverter.scala:39)
	at com.sap.kafka.utils.GenericJdbcTypeConverter.convertToDBType$(GenericJdbcTypeConverter.scala:19)
	at com.sap.kafka.utils.hana.HANAJdbcTypeConverter$.convertToHANAType(HANAJdbcTypeConverter.scala:16)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.$anonfun$add$4(HANASinkRecordsCollector.scala:130)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.$anonfun$add$4$adapted(HANASinkRecordsCollector.scala:126)
	at scala.collection.Iterator.foreach(Iterator.scala:943)
	at scala.collection.Iterator.foreach$(Iterator.scala:943)
	at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
	at scala.collection.IterableLike.foreach(IterableLike.scala:74)
	at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
	at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
	at com.sap.kafka.connect.sink.hana.HANASinkRecordsCollector.add(HANASinkRecordsCollector.scala:126)
	at com.sap.kafka.connect.sink.hana.HANAWriter.$anonfun$write$2(HANAWriter.scala:56)
	at com.sap.kafka.connect.sink.hana.HANAWriter.$anonfun$write$2$adapted(HANAWriter.scala:44)
	at scala.collection.TraversableLike$WithFilter.$anonfun$foreach$1(TraversableLike.scala:912)
	at scala.collection.Iterator.foreach(Iterator.scala:943)
	at scala.collection.Iterator.foreach$(Iterator.scala:943)
	at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
	at scala.collection.IterableLike.foreach(IterableLike.scala:74)
	at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
	at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
	at scala.collection.TraversableLike$WithFilter.foreach(TraversableLike.scala:911)
	at com.sap.kafka.connect.sink.hana.HANAWriter.write(HANAWriter.scala:44)
	at com.sap.kafka.connect.sink.GenericSinkTask.put(GenericSinkTask.scala:36)
	at org.apache.kafka.connect.runtime.WorkerSinkTask.deliverMessages(WorkerSinkTask.java:563)
	... 10 more
```

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker

#### Running

This description assumes Docker and Docker-Compose are available on local machine. 

##### Step 1: Build Docker image for kafka-connector-hana

Use the instruction for `examples/persons1db` to build the Docker image.


##### Step 2: Starting Zookeeper, Kafka, Kafka-Connect, Cassandra Database

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "inventory9db_default" with the default driver
Creating inventory9db_zookeeper_1 ... done
Creating inventory9db_kafka_1     ... done
Creating inventory9db_cassandra_1 ... done
Creating inventory9db_connect_1   ... done
Attaching to inventory9db_zookeeper_1, inventory9db_kafka_1, inventory9db_connect_1, inventory9db_cassandra_1
...
```

To start Cassandra Command Line client (cqlsh), run the following Docker command.

```
$  docker-compose exec cassandra bash -c 'cqlsh --keyspace=testdb'
```

This will start the command line client.

```
$ docker-compose exec cassandra bash -c 'cqlsh --keyspace=testdb'

Warning: Cannot create directory at `/home/cassandra/.cassandra`. Command history will not be saved.

Connected to Test Cluster at 127.0.0.1:9042.
[cqlsh 5.0.1 | Cassandra 3.11.10 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cqlsh:testdb>
```

This Debezium Cassandra Database contains several tables. We will use user `postgres` and table `testdb.customers` in this scenario.

```
cqlsh:testdb> select * from customers;

 id | email                 | first_name | last_name
----+-----------------------+------------+-----------
  2 |    gbailey@foobar.com |     George |    Bailey
  3 |         ed@walker.com |     Edward |    Walker
  4 |    annek@noanswer.org |       Anne | Kretchmar
  1 | sally.thomas@acme.com |      Sally |    Thomas

(4 rows)
cqlsh:testdb> 
```


##### Step 3: Installing HANA connector

We prepare for the connector json files using the json file `connect-hana-sink-9.json`. Adjust the connection properties of `connect-hana-sink-9.json`.

```
{
    "name": "inventory9-hana-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.sink.hana.HANASinkConnector",
        "tasks.max": "1",
        "topics": "test_prefix.testdb.customers",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/kafka/custom-config/secrets.properties:connection1-user}",
        "connection.password": "${file:/kafka/custom-config/secrets.properties:connection1-password}",
        "transforms": "unwrap",
        "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
        "transforms.unwrap.drop.tombstones": "false",
        "transforms.unwrap.delete.handling.mode": "rewrite",
        "auto.create": "true",
        "test_prefix.testdb.customers.insert.mode": "upsert",
        "test_prefix.testdb.customers.delete.enabled": "true",
        "test_prefix.testdb.customers.pk.fields": "id",
        "test_prefix.testdb.customers.pk.mode": "record_key",
        "test_prefix.testdb.customers.table.name": "\"<schemaname>\".\"INVENTORY9_CUSTOMERS\""
    }
}
```

As the Cassandra source connector is running with Cassadra, only the HANA sink connectors is installed to Kafka-Connect.

```
$ curl -X POST -H "Accept:application/json" -H "Content-Type:application/json" -d @connect-hana-sink-9.json http://localhost:8083/connectors/
{"name":"inventory9-hana-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"dbserver2.inventory.customers","connection.url":"jdbc:sap://...
$
$ curl http://localhost:8083/connectors/
["inventory-postgres-source","inventory9-hana-sink"]
$
```

The above result shows that the connectors are successfully deployed.

##### Step 5: Interactively update the Cassndra Table and verify the result in HANA Table

TODO

##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping inventory9db_connect_1   ... done
Stopping inventory9db_cassandra_1 ... done
Stopping inventory9db_kafka_1     ... done
Stopping inventory9db_zookeeper_1 ... done
Removing inventory9db_connect_1   ... done
Removing inventory9db_cassandra_1 ... done
Removing inventory9db_kafka_1     ... done
Removing inventory9db_zookeeper_1 ... done
Removing network inventory9db_default
$ 
```
