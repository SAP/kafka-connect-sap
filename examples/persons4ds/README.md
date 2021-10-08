### Example persons4ds: kafka-hana-connect using strimzi kafka images with docker-compose

This example is a distributed version of example [persons4](../persons4/README.md) using strimizi kafka docker images.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker, Docker-Compose

#### Running

This description assumes Docker and Docker-Compose are available on local machine.

##### Steps 1: Build Docker image for kafka-connector-hana

First, run `make get_libs` to place the required jar files into directory `target`.

```
$ make get_libs
Getting jar files into target ...
$
$ ls target 
apicurio-registry-client-2.0.0.Final.jar                   guava-31.0.1-jre.jar
apicurio-registry-common-2.0.0.Final.jar                   httpclient-4.5.13.jar
apicurio-registry-serde-common-2.0.0.Final.jar             httpcore-4.4.14.jar
apicurio-registry-serdes-avro-serde-2.0.0.Final.jar        keycloak-authz-client-12.0.3.jar
apicurio-registry-serdes-jsonschema-serde-2.0.0.Final.jar  keycloak-common-12.0.3.jar
apicurio-registry-utils-converter-2.0.0.Final.jar          keycloak-core-12.0.3.jar
avro-1.10.2.jar                                            ngdbc-2.10.14.jar
$
```

Download from https://github.com/SAP/kafka-connect-sap/releases the kafka-connector-hana_2.12-x.x.x.jar file that is suitable for your Kafka version and save it in `target` directory.

Next, run `make build_docker` to build the Docker image using strimzi's Kafka image and add the jar files to its plugins directory.

```
$ make docker_build
Building docker image ...
docker build . -t strimzi-connector-hana-min
Sending build context to Docker daemon  1.423MB
Step 1/5 : FROM strimzi/kafka:0.20.1-kafka-2.6.0
 ---> a0be3ed644fc
Step 2/5 : USER root:root
 ---> Using cache
 ---> e651c111cfd0
Step 3/5 : RUN mkdir -p /opt/kafka/plugins/kafka-connector-hana
 ---> Using cache
 ---> b5e5ec331af9
Step 4/5 : COPY ./target/ /opt/kafka/plugins/kafka-connector-hana/
 ---> Using cache
 ---> d04b23041483
Step 5/5 : USER 1001
 ---> Using cache
 ---> 44fb0e9e5347
Successfully built 44fb0e9e5347
Successfully tagged strimzi-connector-hana-min:latest
$
```

##### Step 2: Prepare the source table (Follow Step 4 of example [persons1](../persons1/README.md))

##### Step 3: Starting Zookeeper, Kafka, Kafka-Coonnect

The `docker-compose.yaml` file defines zookeeper, kafka, and connect services. It is noted that Kafka broker uses its advertised host set to `host.docker.internal:9092` assumeing this host name is resolvable from the containers and at the host. This allows Kafka broker to be accessed from the container of Kafka-Connect and from the host for inspection.

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "persons4ds_default" with the default driver
Creating persons4ds_zookeeper_1 ... done
Creating persons4ds_kafka_1     ... done
Creating persons4ds_registry_1  ... done
Creating persons4ds_connect_1   ... done
Attaching to persons4ds_zookeeper_1, persons4ds_kafka_1, persons4ds_registry_1, persons4ds_connect_1
registry_1   | exec java -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/opt/agent-bond/agent-bond.jar=jmx_exporter{{9779:/opt/agent-bond/jmx_exporter_config.yml}} -XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:+ExitOnOutOfMemoryError -cp . -jar /deployments/apicurio-registry-app-1.2.3.Final-runner.jar
...
```

After starting the Docker containers using docker-compose, we can verify whether Kafka Connect is running using curl.

```
$ curl http://localhost:8083/
{"version":"2.8.0","commit":"ebb1d6e21cc92130","kafka_cluster_id":"HECIAPmDQJyky_bOF6iMRQ"}
$
$ curl http://localhost:8083/connectors
[]
$
```

The above result shows that Kafka Connect using Kafka 2.4.1 is running and there is no connector deployed.

##### Step 4: Installing HANA connectors

We prepare for the connector json files using the json files `connect-hana-source-4.json` and `connect-hana-sink-4.json` which are similar to the files created for example `persons1ds` but include the following converter properties to use Apicurio schema registry at docker container address `registry:8080`.

```
{
    "name": "test-topic-4-source",
    "config": {
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/apis/registry/v2",
        "value.converter.apicurio.registry.auto-register": "true",
        "value.converter.apicurio.registry.find-latest": "true"
    }
}
```

```
{
    "name": "test-topic-4-sink",
    "config": {
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/apis/registry/v2",
        "value.converter.apicurio.registry.auto-register": "true",
        "value.converter.apicurio.registry.find-latest": "true"
    }
}
```

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API using curl.

```
$ curl -X POST -H 'content-type:application/json' -d @connect-hana-source-4.json http://localhost:8083/connectors
{"name":"test-topic-4-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_4","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-4.json http://localhost:8083/connectors
{"name":"test-topic-4-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_4","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
["test-topic-4-source","test-topic-4-sink"]
```

The above result shows that the connectors are deployed.


##### Step 5: Verifying the result (Follow Step 6 of example `persons4`)

In addition to inspecting Kafka topic and HANA table, you can verify the registered schema at the schema registry using the following command.

```
$ curl http://localhost:8080/api/artifacts
["test_topic_4-value"]
$ curl -i 'http://localhost:8080/apis/registry/v2/search/artifacts?name=test_topic'
{"artifacts":[{"id":"test_topic_4-value","name":...
```

It is noted that this scenario builds the Docker image with the apicurio schema registry usage and runs Kafka Connect in the distributed mode. Additional connectors can be deployed to this Kafka Connect instance.


##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping persons4ds_registry_1  ... done
Stopping persons4ds_connect_1   ... done
Stopping persons4ds_kafka_1     ... done
Stopping persons4ds_zookeeper_1 ... done
Removing persons4ds_registry_1  ... done
Removing persons4ds_connect_1   ... done
Removing persons4ds_kafka_1     ... done
Removing persons4ds_zookeeper_1 ... done
Removing network persons4ds_default
$ 
```
