### Example persons6: standalone incremental-mode HANA-Connectors using Schema Registry (confluent-registry)

This example is similar to example [persons2](../persons2/README.md) but example persons6 uses a schema registry to store the schema in the registry instead of including the schema physically in every message. This example uses Avro messages and stores schemas in Confluent registry. For using Apicurio registry, see example [persons4](../persons4/README.md).

#### Prerequisites

- This project is built (or its jar file is available)
- Local Kafka installation
- Access to Conluent schema registry
- Access to HANA
- Understanding of example [persons2](../persons2/README.md)

#### Running

This description assumes Kafka 2.4.1 or newer is installed on local machine and environment variables `$KAFKA_HOME` is set to this directory (e.g. `/usr/local/opt/kafka_2.12-2.4.1`) and `$KAFKA_CONNECT_SAP` is set to this repository's root directory.


##### Steps 1-2: Follow Steps 1 and 2 of [persons1 example](../persons1/README.md).

- start Kafka
- install kafka-connector-hana

###### Start Confluent registry

In addition to the above steps described in example [persons1](../persons1/README.md), for this example, Confluent registry must be made available and accessible from the connector. For this example, we use Confluent registry's docker image. Assuming docker is locally installed and hostname `host.docker.internal` is resolved to the internal IP address used by the host, run the following command.

```
$ docker run --name confluent-schema-registry -p 8081:8081 -e SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=host.docker.internal:9092 -e SCHEMA_REGISTRY_HOST_NAME=localhost confluentinc/cp-schema-registry  
===> User
uid=0(root) gid=0(root) groups=0(root)
===> Configuring ...
===> Running preflight checks ... 
===> Check if Kafka is healthy ...
[main] INFO org.apache.kafka.clients.admin.AdminClientConfig - AdminClientConfig values: 
...
```

###### Add Confluent registry client libraries to the plugin directory

In order to use Apicurio registry, its client libraries must be placed in the connector's plugin directory. The required jar files are listed in the [confluent-registry-jars.txt](./confluent-registry-jars.txt) file.

Run the following command to download those jar files into the target directory.

```
$ make get_libs
Getting jar files into target ...
...
$ ls target
avro-1.9.2.jar                          kafka-connect-avro-converter-5.4.2.jar
common-config-5.4.2.jar                 kafka-connector-hana-1.0.0-SNAPSHOT.jar
common-utils-5.4.2.jar                  kafka-schema-registry-client-5.4.2.jar
kafka-avro-serializer-5.4.2.jar         ngdbc-2.5.49.jar
$ 
```

We copy the downloaded jar files into the connector's plugin directory.

```
$ cp target/*.jar $KAFKA_HOME/plugins/kafka-connector-hana
$
$ cd $KAFKA_HOME
$ ls plugins/kafka-connector-hana
avro-1.9.2.jar                          kafka-connect-avro-converter-5.4.2.jar
common-config-5.4.2.jar                 kafka-connector-hana-1.0.0-SNAPSHOT.jar
common-utils-5.4.2.jar                  kafka-schema-registry-client-5.4.2.jar
kafka-avro-serializer-5.4.2.jar         ngdbc-2.5.45.jar
$ 
```

##### Step 3: Prepare the connector configuration files

To use a schema registry, the connector's converter properties must be configured accordingly. First, we make a copy of the default standalone connector configuration file.

```
$ cp $KAFKA_HOME/config/connect-standalone.properties $KAFKA_HOME/config/connect-standalone-confluent-avro.properties
$
```

Modify the converter properties of `connect-standalone-confluent-avro.properties` as shown below. For the converter's registry.url propety, we assume that the registry is running locally at port 8080.

```
value.converter=io.confluent.connect.avro.AvroConverter
value.converter.schema.registry.url=http://localhost:8081
```

For the source and sink configuraiton, we modify the configuration for [persons2](../persons2/README.md).

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-source-2.properties $KAFKA_HOME/config/connect-hana-source-3.properties
$
```
We customize the configuration files so that the records are read from HANA table `PERSONS6` and stored into Kafka topics `test_topic_6`.

```
#
# a sample source configuration for transferring data from table PERSONS6 to topic test_topic_6
# in the batch mode
#
name=test-topic-6-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_6
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
mode=incrementing
test_topic_6.incrementing.column.name=PERSONID
test_topic_6.table.name=<schemaname>."PERSONS6"
```

Similarly, copy the sink connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-sink-2.properties $KAFKA_HOME/config/connect-hana-sink-6.properties
$
```

Similarly, we customize this configuraiton file so that that records are read from Kafka topics `test_topic_6` and stored into HANA table `PERSONS6_RES`. We complete the configuration by setting the connection properties.

```
#
# a sample sink configuration for transferring data from topic test_topic_6 to table PERONS6_RES
#
name=test_topic_6_sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic_6
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
test_topic_6.table.name=<schemaname>."PERSONS6_RES"
```

##### Step 4: Prepare the source table

Assuming table "PERSONS6" does not exist, we create this table and add some records using some SQL tool.
```
CREATE TABLE Persons6 (PersonID int primary key, LastName varchar(255), FirstName varchar(255));
INSERT INTO Persons6 VALUES (1, 'simpson', 'homer');
INSERT INTO Persons6 VALUES (2, 'simpson', 'marge');
INSERT INTO Persons6 VALUES (3, 'simpson', 'bart');
INSERT INTO Persons6 VALUES (4, 'simpson', 'lisa');
INSERT INTO Persons6 VALUES (5, 'simpson', 'maggie');
```

##### Step 5: Starting connectors

We start both the source and sink connectors using connect-standalone.sh with `connect-hana-source-6.properties` and `connect-hana-sink-6.properties`, respectively

```
$bin/connect-standalone.sh config/connect-standalone-confluent-avro.properties config/connect-hana-source-6.properties config/connect-hana-sink-6.properties
[2020-07-24 19:48:17,490] INFO Kafka Connect standalone worker initializing ... (org.apache.kafka.connect.cli.ConnectStandalone:69)
[2020-07-24 19:48:17,499] INFO WorkerInfo values: 
	jvm.args = -Xms256M, -Xmx2G, -XX:+UseG1GC, -XX:MaxGCPauseMillis=20, -XX:InitiatingHeapOccupancyPercent=35
...
```

##### Step 6: Verifing the result

We can look into the Kafka topic `test_topic_6` using the Kafka console consumer to see if the records are stored in this topic.

```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_6 --from-beginning
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
SELECT * FROM Persons6_Res;
PERSONID  LASTNAME  FIRSTNAME
--------  --------  ---------
       1  simpson   homer    
       2  simpson   marge    
       3  simpson   bart     
       4  simpson   lisa     
       5  simpson   maggie
```