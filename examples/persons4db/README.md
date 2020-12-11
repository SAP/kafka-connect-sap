### Example persons4db: kafka-hana-connect using debezium kafka images with docker-compose

This example is a distributed version of example persons4 using debezium kafka docker images.

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
apicurio-registry-client-1.2.3.Final.jar           jersey-client-2.29.1.jar
apicurio-registry-common-1.2.3.Final.jar           jersey-common-2.29.1.jar
apicurio-registry-utils-converter-1.2.3.Final.jar  jersey-hk2-2.29.1.jar
apicurio-registry-utils-serde-1.2.3.Final.jar      jersey-media-jaxb-2.29.1.jar
avro-1.9.2.jar                                     jersey-media-json-binding-2.29.1.jar
cdi-api-2.0.jar                                    jersey-mp-config-2.29.1.jar
geronimo-config-impl-1.2.2.jar                     jersey-mp-rest-client-2.29.1.jar
guava-27.0.1-jre.jar                               jersey-server-2.29.1.jar
jakarta.json-1.1.5.jar                             kafka-connector-hana-1.0.0-SNAPSHOT.jar
jakarta.json-api-1.1.5.jar                         microprofile-config-api-1.4.jar
jakarta.json.bind-api-1.0.2.jar                    microprofile-rest-client-api-1.4.0.jar
javax.interceptor-api-1.2.jar                      ngdbc-2.5.49.jar
jersey-cdi1x-2.29.1.jar                            yasson-1.0.3.jar
$ 
```

Next, run `make build_docker` to build the Docker image using debezium's Kafka-Connect image and add the jar files to its plugins directory.

```
$ make docker_build
Building docker image ...
docker build . -t debezium-connector-hana-rega
Sending build context to Docker daemon  8.154MB
Step 1/5 : FROM debezium/connect:1.2
 ---> 66f074fce2f0
Step 2/5 : USER root:root
 ---> Using cache
 ---> 64a9079cfa93
Step 3/5 : RUN mkdir -p /kafka/connect/kafka-connector-hana
 ---> Using cache
 ---> 174c3e1fb6db
Step 4/5 : COPY ./target/ /kafka/connect/kafka-connector-hana/
 ---> 9bec1e6156e8
Step 5/5 : USER 1001
 ---> Running in 496d5db51336
Removing intermediate container 496d5db51336
 ---> 53b8b7c60ca6
Successfully built 53b8b7c60ca6
Successfully tagged debezium-connector-hana-rega:latest
$ 
```

##### Step 2: Prepare the source table (Follow Step 4 of example `persons1`)

##### Step 3: Starting Zookeeper, Kafka, Kafka-Coonnect

The `docker-compose.yaml` file defines zookeeper, kafka, and connect services. It is noted that Kafka broker uses its advertised host set to `host.docker.internal:9092` assumeing this host name is resolvable from the containers and at the host. This allows Kafka broker to be accessed from the container of Kafka-Connect and from the host for inspection.

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "persons4db_default" with the default driver
Creating persons4db_zookeeper_1 ... done
Creating persons4db_kafka_1     ... done
Creating persons4db_connect_1   ... done
Creating persons4db_registry_1  ... done
Attaching to persons4db_zookeeper_1, persons4db_kafka_1, persons4db_registry_1, persons4db_connect_1
...
```

After starting the Docker containers using docker-compose, we can verify whether Kafka Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Thu, 10 Sep 2020 13:37:32 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.24.v20191120)

{"version":"2.5.0","commit":"66563e712b0b9f84","kafka_cluster_id":"QWEEfZDWQOq2y26f1bHKXg"}
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Thu, 10 Sep 2020 13:38:00 GMT
Content-Type: application/json
Content-Length: 2
Server: Jetty(9.4.24.v20191120)

[]
$
```

The above result shows that Kafka Connect using Kafka 2.5.0 is running and there is no connector deployed.

##### Step 4: Installing HANA connectors

We prepare for the connector json files using the json files `connect-hana-source-4.json` and `connect-hana-sink-4.json` which are similar to the files created for example `persons1ds` but include the following converter properties to use Apicurio schema registry at docker container address `registry:8080`.

```
{
    "name": "test-topic-4-source",
    "config": {
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/api",
        "value.converter.apicurio.registry.converter.serializer": "io.apicurio.registry.utils.serde.AvroKafkaSerializer",
        "value.converter.apicurio.registry.converter.deserializer": "io.apicurio.registry.utils.serde.AvroKafkaDeserializer",
        "value.converter.apicurio.registry.global-id": "io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy"
    }
}
```

```
{
    "name": "test-topic-4-sink",
    "config": {
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/api",
        "value.converter.apicurio.registry.converter.serializer": "io.apicurio.registry.utils.serde.AvroKafkaSerializer",
        "value.converter.apicurio.registry.converter.deserializer": "io.apicurio.registry.utils.serde.AvroKafkaDeserializer",
        "value.converter.apicurio.registry.global-id": "io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy"
    }
}
```

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API using curl.

```
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-source-4.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Thu, 10 Sep 2020 13:38:46 GMT
Location: http://localhost:8083/connectors/test-topic-4-source
Content-Type: application/json
Content-Length: 869
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-4-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_4","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-4.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Thu, 10 Sep 2020 13:40:32 GMT
Location: http://localhost:8083/connectors/test-topic-4-sink
Content-Type: application/json
Content-Length: 884
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-4-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_4","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Thu, 10 Sep 2020 13:41:24 GMT
Content-Type: application/json
Content-Length: 43
Server: Jetty(9.4.24.v20191120)

["test-topic-4-sink","test-topic-4-source"]
$
```

The above result shows that the connectors are deployed.

##### Step 5: Verifying the result (Follow Step 6 of example `persons4`)

In addition to inspecting Kafka topic and HANA table, you can verify the registered schema at the schema registry using the following command.

```
$ curl -i http://localhost:8080/api/artifacts
HTTP/1.1 200 OK
Date: Thu, 10 Sep 2020 12:17:36 GMT
Expires: Wed, 09 Sep 2020 12:17:36 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 43

["test_topic_4-value"]
$
$ curl -i http://localhost:8080/api/artifacts/test_topic_4-value
HTTP/1.1 200 OK
Date: Thu, 10 Sep 2020 12:17:56 GMT
Expires: Wed, 09 Sep 2020 12:17:56 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 240

{"type":"record","name":"d025803persons4","fields":[{"name":"PERSONID","type":"int"},{"name":"LASTNAME","type":["null","string"],"default":null},{"name":"FIRSTNAME","type":["null","string"],"default":null}],"connect.name":"d025803persons4"}
$
```

It is noted that this scenario builds the Docker image with the apicurio schema registry usage and runs Kafka Connect in the distributed mode. Additional connectors can be deployed to this Kafka Connect instance.


##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping persons4db_registry_1  ... done
Stopping persons4db_connect_1   ... done
Stopping persons4db_kafka_1     ... done
Stopping persons4db_zookeeper_1 ... done
Removing persons4db_registry_1  ... done
Removing persons4db_connect_1   ... done
Removing persons4db_kafka_1     ... done
Removing persons4db_zookeeper_1 ... done
Removing network persons4db_default
$ 
```
