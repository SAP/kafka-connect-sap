### Example persons1: standalone batch-mode Source and Sink HANA-Connectors

This example shows a simple batch-mode record transfer between Kafka and HANA using standalone connectors.

#### Prerequisites

- This project is built (or its jar file is available)
- Local Kafka installation
- Access to HANA

#### Running

This description assumes Kafka 2.4.1 is installed on local machine and environment variables `$KAFKA_HOME` is set to this directory (e.g. `/usr/local/opt/kafka_2.12-2.4.1`) and `$KAFKA_CONNECT_SAP` is set to this repository's root directory.


##### Step 1: Start Zookeeper and Kafka

First, we start both Zookeeper and Kafka using the default configuration.

```
$ cd $KAFKA_HOME
$ bin/zookeeper-server-start.sh config/zookeeper.properties
$ ...
$ bin/kafka-server-start.sh config/server.properties
$ ...
```

For more information regarding how to start Kafka, refer to https://kafka.apache.org/quickstart.

##### Step 2: Install the jar files for kafka-connector-hana

We install the jar files into a dedicated directory within the plugins directory `plugins` that we create at `$KAFKA_HOME`.

First, we create a plugins directory `$KAFKA_HOME/plugins` if not yet created and create directory `kafka-connector-hana` within this directory. 
```
$ mkdir -p $KAFKA_HOME/plugins/kafka-connector-hana
$
```

Assuming this project has been built (see Building), run `mvn install` to place the required jar files including the HANA jdbc driver into directory 'target'.

```
$ mvn install
...
$ ls target
kafka-connector-hana-1.0.0-SNAPSHOT.jar  ngdbc-2.5.49.jar
$
```
Copy those jar files into `$KAFKA_HOME/plugins/kafka-connector-hana` directory.

```
$ cp target/*.jar $KAFKA_HOME/plugins/kafka-connector-hana
$
```

##### Step 3: Prepare the connector configuration files

First, modify the standalone configuration file `$KAFKA_HOME/config/connect-standalone.properties` so that its property `plugin.path` points to the plugins folder.

```
plugin.path=./plugins
```

We use the provided configuration files and customize some properties. First, copy the source connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-source-1.properties $KAFKA_HOME/config
$
```

This source connector configuration file assumes that records are read from HANA table `PERSONS1` and stored into Kafka topics `test_topic_1`. We complete the configuration by setting the `url`, `username`, and `password` values for the HANA connection as well as the table's `schemaname`. In this configuration, the connection user and password are provided in the connector configuration file. To externalize these values, use `ConfigProvider`. 

```
#
# a sample source configuration for transferring data from table PERSONS1 to topic test_topic_1
# in the batch mode
#
name=test-topic-1-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_1
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
test_topic_1.table.name=<schemaname>."PERSONS1"
```

Similarly, copy the sink connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-sink-1.properties $KAFKA_HOME/config
$
```

This sink connector configuraiton file assumes that records are read from Kafka topics `test_topic_1` and stored into HANA table `PERSONS1_RES`. We complete the configuration by setting the connection properties.

```
#
# a sample sink configuration for transferring data from topic test_topic_1 to table PERONS1_RES
#
name=test_topic_1_sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic_1
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
test_topic_1.table.name=<schemaname>."PERSONS1_RES"
```

##### Step 4: Prepare the source table

Assuming table "PERSONS1" does not exist, we create this table and add some records using some SQL tool.
```
CREATE TABLE Persons1 (PersonID int, LastName varchar(255), FirstName varchar(255));
INSERT INTO Persons1 VALUES (1, 'simpson', 'homer');
INSERT INTO Persons1 VALUES (2, 'simpson', 'marge');
INSERT INTO Persons1 VALUES (3, 'simpson', 'bart');
INSERT INTO Persons1 VALUES (4, 'simpson', 'lisa');
INSERT INTO Persons1 VALUES (5, 'simpson', 'maggie');
```

##### Step 5: Starting connectors

We start both the source and sink connectors using connect-standalone.sh with `connect-hana-source-1.properties` and `connect-hana-sink-1.properties`, respectively.

```
$ bin/connect-standalone.sh config/connect-standalone.properties config/connect-hana-source-1.properties config/connect-hana-sink-1.properties
[2020-07-24 19:48:17,490] INFO Kafka Connect standalone worker initializing ... (org.apache.kafka.connect.cli.ConnectStandalone:69)
[2020-07-24 19:48:17,499] INFO WorkerInfo values: 
	jvm.args = -Xms256M, -Xmx2G, -XX:+UseG1GC, -XX:MaxGCPauseMillis=20, -XX:InitiatingHeapOccupancyPercent=35
...
```

##### Step 6: Verifing the result

We can look into the Kafka topic `test_topic_1` using the Kafka console consumer to see if the records are stored in this topic.

```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_1 --from-beginning
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"merge"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":3,"LASTNAME":"simpson","FIRSTNAME":"bart"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":4,"LASTNAME":"simpson","FIRSTNAME":"lisa"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":5,"LASTNAME":"simpson","FIRSTNAME":"maggie"}}
...
```

The default configuration uses a JSON message that includes both the schema and payload.


We can look into the target table using SQL.

```
SELECT * FROM Persons4_Res;
PERSONID  LASTNAME  FIRSTNAME
--------  --------  ---------
       1  simpson   homer    
       2  simpson   merge    
       3  simpson   bart     
       4  simpson   lisa     
       5  simpson   maggie
...
```

It is noted that this scenario uses the batch mode. As a result, the records will be read periodically from the source table and inserted into the sink table multiple times. For the non-batch mode, which is called the incremental mode, see [person2 example](../persons2).

