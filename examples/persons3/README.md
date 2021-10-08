### Example persons3: standalone incremental-mode HANA-Connectors using Schema Registry (apicurio-registry)

This example is similar to example [persons2](../persons2/README.md) but example persons3 uses a schema registry to store the schema in the registry instead of including the schema physically in every message. This example uses JSON messages and stores schemas in Apicurio registry. For using Avro messages with Apicurio registry, see example [persons4](../persons4/README.md).

#### Prerequisites

- This project is built (or its jar file is available)
- Local Kafka installation
- Access to HANA
- Understanding of example [persons2](../persons2/README.md)

#### Running

This description assumes Kafka 2.4.1 or newer is installed on local machine and environment variables `$KAFKA_HOME` is set to this directory (e.g. `/usr/local/opt/kafka_2.12-2.4.1`) and `$KAFKA_CONNECT_SAP` is set to this repository's root directory.


##### Steps 1-2: Follow Steps 1 and 2 of [persons1 example](../person1/README.md).

- start Kafka
- install kafka-connector-hana

###### Start Apicurio registry

In addition to the above steps described in example [persons1](../persons1/README.md), for this example, Apicurio registry must be made available and accessible from the connector. For this example, we use Apicurio registry's docker image. Assuming docker is locally installed, run the following command.

```
$ docker run -it --rm -p 8080:8080 apicurio/apicurio-registry-mem:2.0.0.Final
exec java -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/opt/agent-bond/agent-bond.jar=jmx_exporter{{9779:/opt/agent-bond/jmx_exporter_config.yml}} -XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:+ExitOnOutOfMemor
...
```

After the registry is started, verify its status by invoking its health check operation, which should return the response similar to the one shown below.

```
$ curl http://localhost:8080/health 

{
    "status": "UP",
    "checks": [
        {
            "name": "ResponseErrorLivenessCheck",
            "status": "UP",
            "data": {
                "errorCount": 0
            }
        },
        {
            "name": "PersistenceTimeoutReadinessCheck",
            "status": "UP",
            "data": {
                "errorCount": 0
            }
        },
        {
            "name": "PersistenceExceptionLivenessCheck",
            "status": "UP",
            "data": {
                "errorCount": 0
            }
        },
        {
            "name": "StorageLivenessCheck",
            "status": "UP"
        },
        {
            "name": "PersistenceSimpleReadinessCheck",
            "status": "UP"
        },
        {
            "name": "ResponseTimeoutReadinessCheck",
            "status": "UP",
            "data": {
                "errorCount": 0
            }
        }
    ]
}
$ 
```

###### Add Apicurio registry client libraries to the plugin directory

In order to use Apicurio registry, its client libraries must be placed in the connector's plugin directory. The required jar files are downloaded by running the following command.


```
$ make get_libs
Getting jar files into target ...
...
$
$ ls target
apicurio-registry-client-2.0.0.Final.jar                   guava-31.0.1-jre.jar
apicurio-registry-common-2.0.0.Final.jar                   httpclient-4.5.13.jar
apicurio-registry-serde-common-2.0.0.Final.jar             httpcore-4.4.14.jar
apicurio-registry-serdes-avro-serde-2.0.0.Final.jar        keycloak-authz-client-12.0.3.jar
apicurio-registry-serdes-jsonschema-serde-2.0.0.Final.jar  keycloak-common-12.0.3.jar
apicurio-registry-utils-converter-2.0.0.Final.jar          keycloak-core-12.0.3.jar
avro-1.10.2.jar
$ 
```

We copy the downloaded jar files into the connector's plugin directory.

```
$ cp target/*.jar $KAFKA_HOME/plugins/kafka-connector-hana
$
```

##### Step 3: Prepare the connector configuration files

To use a schema registry, the connector's converter properties must be configured accordingly. First, we make a copy of the default standalone connector configuration file.

```
$ cp $KAFKA_HOME/config/connect-standalone.properties $KAFKA_HOME/config/connect-standalone-apicurio-json.properties
$
```

Modify the converter properties as shown below. For the converter's registry.url propety, we assume that the registry is running locally at port 8080.

```
value.converter=io.apicurio.registry.utils.converter.ExtJsonConverter
value.converter.apicurio.registry.url=http://localhost:8080/apis/registry/v2
value.converter.apicurio.registry.auto-register=true
value.converter.apicurio.registry.find-latest=true
```

For the source and sink configuraiton, we modify the configuration for [persons2](../persons2/README.md).

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-source-2.properties $KAFKA_HOME/config/connect-hana-source-3.properties
$
```
We customize the configuration files so that the records are read from HANA table `PERSONS3` and stored into Kafka topics `test_topic_3`.

```
#
# a sample source configuration for transferring data from table PERSONS3 to topic test_topic_3
# in the batch mode
#
name=test-topic-3-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_3
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
mode=incrementing
test_topic_3.incrementing.column.name=PERSONID
test_topic_3.table.name=<schemaname>."PERSONS3"
```

Similarly, copy the sink connector configuration file to the target `config` directory.

```
$ cp $KAFKA_CONNECT_SAP/config/connect-hana-sink-2.properties $KAFKA_HOME/config/connect-hana-sink-3.properties
$
```

Similarly, we customize this configuraiton file so that that records are read from Kafka topics `test_topic_3` and stored into HANA table `PERSONS3_RES`. We complete the configuration by setting the connection properties.

```
#
# a sample sink configuration for transferring data from topic test_topic_3 to table PERONS3_RES
#
name=test_topic_3_sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic_3
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
test_topic_3.table.name=<schemaname>."PERSONS3_RES"
```

##### Step 4: Prepare the source table

Assuming table "PERSONS3" does not exist, we create this table and add some records using some SQL tool.
```
CREATE TABLE Persons3 (PersonID int primary key, LastName varchar(255), FirstName varchar(255));
INSERT INTO Persons3 VALUES (1, 'simpson', 'homer');
INSERT INTO Persons3 VALUES (2, 'simpson', 'marge');
INSERT INTO Persons3 VALUES (3, 'simpson', 'bart');
INSERT INTO Persons3 VALUES (4, 'simpson', 'lisa');
INSERT INTO Persons3 VALUES (5, 'simpson', 'maggie');
```

##### Step 5: Starting connectors

We start both the source and sink connectors using connect-standalone.sh with `connect-hana-source-3.properties` and `connect-hana-sink-3.properties`, respectively

```
$ bin/connect-standalone.sh config/connect-standalone-apicurio-json.properties config/connect-hana-source-3.properties config/connect-hana-sink-3.properties
[2020-07-24 19:48:17,490] INFO Kafka Connect standalone worker initializing ... (org.apache.kafka.connect.cli.ConnectStandalone:69)
[2020-07-24 19:48:17,499] INFO WorkerInfo values: 
	jvm.args = -Xms256M, -Xmx2G, -XX:+UseG1GC, -XX:MaxGCPauseMillis=20, -XX:InitiatingHeapOccupancyPercent=35
...
```

##### Step 6: Verifing the result

We can look into the Kafka topic `test_topic_3` using the Kafka console consumer to see if the records are stored in this topic.

```
$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_3 --from-beginning
{"schemaId":1,"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schemaId":1,"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"marge"}}
{"schemaId":1,"payload":{"PERSONID":3,"LASTNAME":"simpson","FIRSTNAME":"bart"}}
{"schemaId":1,"payload":{"PERSONID":4,"LASTNAME":"simpson","FIRSTNAME":"lisa"}}
{"schemaId":1,"payload":{"PERSONID":5,"LASTNAME":"simpson","FIRSTNAME":"maggie"}}
```

This configuration uses JSON messages and each message includes only the schema ID and not the schema itself.
