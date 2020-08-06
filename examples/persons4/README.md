### Example persons4: standalone incremental-mode HANA-Connectors using Schema Registry (apicurio-registry)

This example is similar to example [persons3(../persons3) but example persons4 uses Avro messages and stores schemas in Apicurio registry. For using JSON messages with Apicurio registry, see example [persons3](../persons3).

#### Prerequisites

- This project is built (or its jar file is available)
- Local Kafka installation
- Access to Apicurio schema registry
- Access to HANA
- Understanding of example [persons2](../persons2)

#### Running

This description assumes Kafka 2.4.1 is installed on local machine and environment variables `$KAFKA_HOME` is set to this directory (e.g. `/usr/local/opt/kafka_2.12-2.4.1`) and `$KAFKA_CONNECT_SAP` is set to this repository's root directory.


##### Steps 1-2: Follow Steps 1 and 2 of [persons3 example](../person3).

- start Kafka
- install kafka-connect-hana
- start Apicurio registry
- install Apicurio client jars


##### Step 3: Prepare the connector configuration files

To use a schema registry, the connector's converter properties must be configured accordingly. First, we make a copy of the default standalone connector configuration file.

```
$ cp $KAFKA_HOME/config/connect-standalone.properties $KAFKA_HOME/config/connect-standalone-apicurio-avro.properties
$
```

Modify the converter properties as shown below. For the converter's registry.url propety, we assume that the registry is running locally at port 8080.

```
value.converter=io.apicurio.registry.utils.converter.AvroConverter
value.converter.apicurio.registry.url=http://localhost:8080/api
value.converter.apicurio.registry.converter.serializer=io.apicurio.registry.utils.serde.AvroKafkaSerializer
value.converter.apicurio.registry.converter.deserializer=io.apicurio.registry.utils.serde.AvroKafkaDeserializer
value.converter.apicurio.registry.global-id=io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy
```

For the source and sink configuraiton, we modify the configuration for persons2.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-source-2.properties $KAFKA_HOME/config/connect-hana-source-4.properties
$
```
We customize the configuration files so that the records are read from HANA table `PERSONS3` and stored into Kafka topics `test_topic_4`.

```
#
# a sample source configuration for transferring data from table PERSONS4 to topic test_topic_4
# in the batch mode
#
name=test-topic-4-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_4
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
mode=incrementing
test_topic_4.incrementing.column.name=PERSONID
test_topic_4.table.name=<schemaname>."PERSONS4"
```

Similarly, copy the sink connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-sink-2.properties $KAFKA_HOME/config/connect-hana-sink-4.properties
$
```

Similarly, we customize this configuraiton file so that that records are read from Kafka topics `test_topic_4` and stored into HANA table `PERSONS4_RES`. We complete the configuration by setting the connection properties.

```
#
# a sample sink configuration for transferring data from topic test_topic_4 to table PERONS4_RES
#
name=test_topic_4_sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic_4
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
test_topic_4.table.name=<schemaname>."PERSONS4_RES"
```

##### Step 4: Prepare the source table

Assuming table "PERSONS4" does not exist, we create this table and add some records using some SQL tool.
```
CREATE TABLE Persons4 (PersonID int primary key, LastName varchar(255), FirstName varchar(255));
INSERT INTO Persons4 VALUES (1, 'simpson', 'homer');
INSERT INTO Persons4 VALUES (2, 'simpson', 'marge');
INSERT INTO Persons4 VALUES (3, 'simpson', 'bart');
INSERT INTO Persons4 VALUES (4, 'simpson', 'lisa');
INSERT INTO Persons4 VALUES (5, 'simpson', 'maggie');
```

##### Step 5: Starting connectors

We start both the source and sink connectors using connect-standalone.sh with `connect-hana-source-4.properties` and `connect-hana-sink-4.properties`, respectively

```
$ bin/connect-standalone.sh config/connect-standalone-apicurio-avro.properties config/connect-hana-source-4.properties config/connect-hana-sink-4.properties
[2020-07-24 19:48:17,490] INFO Kafka Connect standalone worker initializing ... (org.apache.kafka.connect.cli.ConnectStandalone:69)
[2020-07-24 19:48:17,499] INFO WorkerInfo values: 
	jvm.args = -Xms256M, -Xmx2G, -XX:+UseG1GC, -XX:MaxGCPauseMillis=20, -XX:InitiatingHeapOccupancyPercent=35
...
```

##### Step 6: Verifing the result

We can look into the Kafka topic `test_topic_4` using the Kafka console consumer to see if the records are stored in this topic.


```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_4 --from-beginning
simpson
homer
simpson
marge
simpsobart
simpsolisa

simpson
       maggie
```

We can look into the target table as well.

```
SELECT * FROM Persons4_Res;
PERSONID  LASTNAME  FIRSTNAME
--------  --------  ---------
       1  simpson   homer    
       2  simpson   marge    
       3  simpson   bart     
       4  simpson   lisa     
       5  simpson   maggie   
```
