### Example persons1db: kafka-hana-connect using debezium kafka images with docker

This example is a distributed version of example persons1 using debezium kafka-connect docker images.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker

#### Running

This description assumes Docker and Docker-Compose are available on local machine. 

##### Steps 1: Build Docker image for kafka-connector-hana

First, run `make get_libs` to place the required jar files into directory `target`.

```
$ make get_libs
Getting jar files into target ...
$
$ ls target 
guava-20.0.jar                       ngdbc-2.5.49.jar
$
```

Download the appropriate kafka-connector-hana_2.12-x.x.x.jar file from https://github.com/SAP/kafka-connect-sap/releases and save it in `target` directory.


Next, run `make build_docker` to build the Docker image using debezium's Kafka Connect image and add the jar files to its plugins directory.


```
$ make docker_build
Building docker image ...
docker build . -t debezium-connector-hana-min
Sending build context to Docker daemon  3.868MB
Step 1/5 : FROM debezium/connect:1.4.2.Final
 ---> 66f074fce2f0
Step 2/5 : USER root:root
 ---> Using cache
 ---> 64a9079cfa93
Step 3/5 : RUN mkdir -p /kafka/connect/kafka-connector-hana
 ---> Using cache
 ---> 174c3e1fb6db
Step 4/5 : COPY ./target/ /kafka/connect/kafka-connector-hana/
 ---> Using cache
 ---> 8f300532bf25
Step 5/5 : USER 1001
 ---> Using cache
 ---> b38cc3546555
Successfully built b38cc3546555
Successfully tagged debezium-connector-hana-min:latest
$ 
```

##### Step 2: Prepare the source table (Follow Step 4 of example `persons1`)

##### Step 3: Starting Zookeeper, Kafka, Kafka-Connect

The docker-compose.yaml file defines zookeeper, kafka, and connect services. It is noted that Kafka broker uses its advertised host set to `host.docker.internal:9092` assumeing this host name is resolvable from the containers and at the host. This allows Kafka broker to be accessed from the container of Kafka-Connect and from the host for inspection.

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "persons1db_default" with the default driver
Creating persons1db_zookeeper_1 ... done
Creating persons1db_kafka_1     ... done
Creating persons1db_connect_1   ... done
Attaching to persons1db_zookeeper_1, persons1db_kafka_1, persons1db_connect_1
...
```

After starting the Docker containers using docker-compose, we can verify whether Kafka-Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:44:49 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.24.v20191120)

{"version":"2.5.0","commit":"66563e712b0b9f84","kafka_cluster_id":"1NEvm9a4TW2t-f5Jkk4peg"}
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:45:35 GMT
Content-Type: application/json
Content-Length: 2
Server: Jetty(9.4.24.v20191120)

[]
$
```

The above result shows that Kafka Connect using Kafka 2.5.0 is running and there is no connector deployed.

##### Step 4: Installing HANA connectors

We prepare for the connector json files using the json files `connect-hana-source-1.json` and `connect-hana-sink-1.json` which are the json representation of the configuration files created for example `persons1`. However, for this distributed example, the user and password values are placed in a separate configuration file `custom-config/hana-secrets.properties` and referenced in the conector json files. Adjust those values accordingly.

```
{
    "name": "test-topic-1-source",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "test_topic_1",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/kafka/custom-config/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/kafka/custom-config/hana-secrets.properties:connection1-password}",
        "test_topic_1.table.name": "\"<schemaname>\".\"PERSONS1\""
    }
}
```

```
{
    "name": "test-topic-1-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "test_topic_1",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/kafka/custom-config/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/kafka/custom-config/hana-secrets.properties:connection1-password}",
        "auto.create": "true",
        "test_topic_1.table.name": "\"<schemaname>\".\"PERSONS1_RES\""
    }
}
```

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API.

```
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-source-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:46:30 GMT
Location: http://localhost:8083/connectors/test-topic-1-source
Content-Type: application/json
Content-Length: 530
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-1-source","config":{"connector.class":"com.sap.kafka.connect.source.hana.HANASourceConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i -X POST -H 'content-type:application/json' -d @connect-hana-sink-1.json http://localhost:8083/connectors
HTTP/1.1 201 Created
Date: Wed, 09 Sep 2020 22:46:39 GMT
Location: http://localhost:8083/connectors/test-topic-1-sink
Content-Type: application/json
Content-Length: 545
Server: Jetty(9.4.24.v20191120)

{"name":"test-topic-1-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_1","connection.url":"jdbc:sap://...
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:47:35 GMT
Content-Type: application/json
Content-Length: 43
Server: Jetty(9.4.24.v20191120)

["test-topic-1-source","test-topic-1-sink"]
$
```

The above result shows that the connectors are deployed.

##### Step 5: Verifying the result (Follow Step 6 of example persions1 and/or persons2)

It is noted that this scenario builds the Docker image without schema registry usage and runs Kafka Connect in the distributed mode. Additional connectors can be deployed to this Kafka Connect instance which use the same distributed-connect.properties configuration.

##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping persons1db_connect_1   ... done
Stopping persons1db_kafka_1     ... done
Stopping persons1db_zookeeper_1 ... done
Removing persons1db_connect_1   ... done
Removing persons1db_kafka_1     ... done
Removing persons1db_zookeeper_1 ... done
Removing network persons1db_default
$ 
```
