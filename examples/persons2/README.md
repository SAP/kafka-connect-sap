### Example persons2: standalone incremental-mode Source and Sink HANA-Connectors

This example shows an incremental-mode record transfer between Kafka and HANA using standalone connectors.

#### Prerequisites

- This project is built (or its jar file is available)
- Local Kafka installation
- Access to HANA

#### Running

This description assumes Kafka 2.4.1 is installed on local machine and environment variables `$KAFKA_HOME` is set to this directory (e.g. `/usr/local/opt/kafka_2.12-2.4.1`) and `$KAFKA_CONNECT_SAP` is set to this repository's root directory.


##### Steps 1-2: Follow Steps 1 and 2 of [persons1 example](../persons1).

##### Step 3: Prepare the connector configuration files

We use the provided configuration files and customize some properties. First, copy the source connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-source-2.properties $KAFKA_HOME/config
$
```

This source connector configuration file assumes that records are read from HANA table `PERSONS2` and stored into Kafka topics `test_topic_2`. We complete the configuration by setting the `url`, `username`, and `password` values for the HANA connection as well as the table's `schemaname`. In this configuration file, the mode property is set to `incrementing` and the key column for the incremental processing is specified.

```
#
# a sample source configuration for transferring data from table PERSONS2 to topic test_topic_2
# in the batch mode
#
name=test-topic-2-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_2
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
mode=incrementing
test_topic_2.incrementing.column.name=PERSONID
test_topic_2.table.name=<schemaname>."PERSONS2"
```

Similarly, copy the sink connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-sink-2.properties $KAFKA_HOME/config
$
```

This sink connector configuraiton file assumes that records are read from Kafka topics `test_topic_2` and stored into HANA table `PERSONS2_RES`. We complete the configuration by setting the connection properties.

```
#
# a sample sink configuration for transferring data from topic test_topic_2 to table PERONS2_RES
#
name=test_topic_2_sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic_2
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
test_topic_2.table.name=<schemaname>."PERSONS2_RES"
```

##### Step 4: Prepare the source table

Assuming table "PERSONS2" does not exist, we create this table and add some records using some SQL tool.
```
CREATE TABLE Persons2 (PersonID int primary key, LastName varchar(255), FirstName varchar(255));
INSERT INTO Persons2 VALUES (1, 'simpson', 'homer');
INSERT INTO Persons2 VALUES (2, 'simpson', 'marge');
INSERT INTO Persons2 VALUES (3, 'simpson', 'bart');
INSERT INTO Persons2 VALUES (4, 'simpson', 'lisa');
INSERT INTO Persons2 VALUES (5, 'simpson', 'maggie');
```

##### Step 5: Starting connectors

We start both the source and sink connectors using connect-standalone.sh with `connect-hana-source-2.properties` and `connect-hana-sink-2.properties`, respectively

```
$ bin/connect-standalone.sh config/connect-standalone.properties config/connect-hana-source-2.properties config/connect-hana-sink-2.properties
[2020-07-24 19:48:17,490] INFO Kafka Connect standalone worker initializing ... (org.apache.kafka.connect.cli.ConnectStandalone:69)
[2020-07-24 19:48:17,499] INFO WorkerInfo values: 
	jvm.args = -Xms256M, -Xmx2G, -XX:+UseG1GC, -XX:MaxGCPauseMillis=20, -XX:InitiatingHeapOccupancyPercent=35
...
```

##### Step 6: Verifing the result

We can look into the Kafka topic `test_topic_2` using the Kafka console consumer to see if the records are stored in this topic.

```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_2 --from-beginning
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"marge"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":3,"LASTNAME":"simpson","FIRSTNAME":"bart"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":4,"LASTNAME":"simpson","FIRSTNAME":"lisa"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":5,"LASTNAME":"simpson","FIRSTNAME":"maggie"}}
```

The default configuration uses a JSON message that includes both the schema and payload.

We can look into the target table as well.

```
SELECT * FROM Person2_Res;
1	simpson	homer
2	simpson	merge
3	simpson	bart
4	simpson	lisa
5	simpson	maggie
```

We insert additional records to the source table.

```
INSERT INTO Persons2 VALUES (11, 'flanders', 'ned');
INSERT INTO Persons2 VALUES (12, 'flanders', 'edna');
INSERT INTO Persons2 VALUES (13, 'flanders', 'rod');
INSERT INTO Persons2 VALUES (14, 'flanders', 'todd');
```

We should see new records are placed in Kafka topic `test_topic_2` and read by the Kafka console consumer.

```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_2 --from-beginning
...
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":11,"LASTNAME":"flanders","FIRSTNAME":"ned"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":12,"LASTNAME":"flanders","FIRSTNAME":"edna"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":13,"LASTNAME":"flanders","FIRSTNAME":"rod"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons2"},"payload":{"PERSONID":14,"LASTNAME":"flanders","FIRSTNAME":"todd"}}
```

We should also find these new records in the target table.

```
SELECT * FROM Person2_Res;
1	simpson	homer
2	simpson	merge
3	simpson	bart
4	simpson	lisa
5	simpson	maggie
11	flanders	ned
12	flanders	edna
13	flanders	rod
14	flanders	todd
```

It is noted that this scenario uses the incremental mode. As a result, only the records will be read periodically from the source table and inserted into the sink table. 

