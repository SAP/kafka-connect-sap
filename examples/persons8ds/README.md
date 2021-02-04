### Example persons8ds: file-source to kafka-hana-connect using strimzi kafka images with docker-compose

This example uses [persons1ds](../persons1ds/README.md) and simply install file-source and hana-sink connectors to load records from
a file into HANA.

#### Prerequisites

- Example persons1ds is setup

#### Running

The below description assumes the setup of [persons1ds](../persons1ds/README.md) is reused.

##### Step 3: Starting Zookeeper, Kafka, Kafka-Connect

Refer to `persons1ds` to start the containers.

##### Step 4: Installing connectors

We prepare for the connector json files `connect-file-source-8.json` and `connect-hana-sink-8.json` Adjust the HANA's connection properties accordingly.

```
{
    "name": "test-topic-8-source",
    "config": {
        "connector.class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
        "tasks.max": "1",
        "topic": "test_topic_8",
        "file": "/tmp/file-topic-8.txt",
        "value.converter": "org.apache.kafka.connect.storage.StringConverter"
    }
}
```

```
{
    "name": "test-topic-8-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "test_topic_8",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/opt/kafka/custom-config/hana-secrets.properties:connection1-password}",
        "auto.create": "true",
        "test_topic_8.table.name": "\"<schemaname>\".\"PERSONS8\""
    }
}
```

Finally, we deploy the connectors by posting the connector configuration json files to the Kafka Connect's API using curl.

```
$ curl -X POST -H 'content-type:application/json' -d @connect-file-source-8.json http://localhost:8083/connectors
{"name":"test-topic-8-source","config":{"connector.class":"org.apache.kafka.connect.file.FileStreamSourceConnector","tasks.max":"1","topic":"test_topic_8","file":"/tmp/file-topic-8.txt","value.converter":"org.apache.kafka.connect.storage.StringConverter","name":"test-topic-8-source"},"tasks":[],"type":"source"}
$
$ curl -X POST -H 'content-type:application/json' -d @connect-hana-sink-8.json http://localhost:8083/connectors
{"name":"test-topic-8-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"test_topic_8","connection.url":"jdbc:sap://...
$
$ curl http://localhost:8083/connectors
["test-topic-8-source","test-topic-8-sink"]
```

The above result shows that the connectors are successfully deployed.


##### Step 5: Verifying the result

As the input file, we use `file-topic-8.txt` which contains a series of JSON strings, each having its schema and payload.

```
$ cat file-topic-8.txt
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"persons8"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"persons8"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"merge"}}
...
```

We copy this file to the conainers `/tmp` directory so that it is picked up by the file source connector. We determine the container ID of Kafka-Connect and `docker cp` to copy this file to the container's `/tmp` directory.


```
$ docker ps | grep connector-hana-min
3706767ab69e   strimzi-connector-hana-min:latest   "sh -c 'bin/connect-…"   2 minutes ago   Up 2 minutes   0.0.0.0:8083->8083/tcp   persons1ds_connect_1
$ docker cp file-topic-8.txt 3706767ab69e:/tmp
$
```

Once the file source connector processes the records, the records will be sent to the HANA sink connector and inserted into table `PERSONS8`.

```
SELECT * FROM PERSONS8;

PERSONID  LASTNAME  FIRSTNAME
--------  --------  ---------
       1  simpson   homer    
       2  simpson   merge    
       3  simpson   bart     
       4  simpson   lisa     
       5  simpson   maggie
```


##### Step 6: Shut down

Refer to `persons1ds` to shut down the containers.


