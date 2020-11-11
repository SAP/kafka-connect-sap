# Kafka Connectors for SAP

Kafka Connect SAP is a generic set of connectors, using the Apache Kafka Connect framework for reliably connecting Kafka with SAP systems

Table of contents
-------------

* [Install](#install)
* [QuickStart](#quickstart)
* [Configuration](#configuration)
* [Examples](#examples)


## Install

To install the connector from source, 

Clone this repository to your local desktop, and then bring up a command prompt in the directory.

and use the following command.

```
mvn clean install -DskipTests
```

which should produce the Kafka Connector jar file `kafka-connect-hana-n.n.jar` in the `target` folder, where `n.n` corresponds to the current version.

**Include the Jdbc Jar**

#####   For SAP Hana

- Follow the steps in [SAP HANA Client Interface Programming Reference](https://help.sap.com/viewer/f1b440ded6144a54ada97ff95dac7adf/LATEST/en-US/434e2962074540e18c802fd478de86d6.html) guide to access the SAP HANA Jdbc jar.
- The maven coordinate of the driver is `com.sap.cloud.db.jdbc:ngdbc:x.x.x` and the drivers are available at the central maven repository [https://search.maven.org/artifact/com.sap.cloud.db.jdbc/ngdbc](https://search.maven.org/artifact/com.sap.cloud.db.jdbc/ngdbc).

## QuickStart

This instruction assumes Kafka installation is locally available so that you can start Kafka Connect as a standalone instance using its `bin/connect-standalone` command. 

For getting started with this connector, the following steps need to be completed.

- Assume there is a table in Hana suitable for this sample. In the following, it is assumed that there is a table named `PERSONS1` with the following SQL schema `(PersonID int primary key, LastName varchar(255), FirstName varchar(255))`.

- Create the config file for source named [`connect-hana-source-1.properties`](config/connect-hana-source-1.properties) and placed it in folder `config`.


```
name=test-topic-1-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=test_topic_1
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
test_topic_1.table.name=<schemaname>."PERSONS1"
```

The above configuration says this source connector should read records from Hana table `PERSONS1` and send them to Kafka topic `test_topic_1`.

- Create the config file for sink named [`connect-hana-sink-1.properties`](config/connect-hana-sink-1.properties) and place it in folder `config`.


```
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

The above configuration says this sink connector should read messages from Kafka topic `test_topic_1` and insert to Hana table `PERSONS1_RES`.

- Start the above kafka-connect source and sink connectors using the standalone connector properties [`connect-standalone.properties`](config/connect-standalone.properties) with the following command.


```
./bin/connect-standalone config/connect-standalone.properties config/connect-hana-test-source-1.properties config/connect-hana-test-sink-1.properties
```

The above scenario is the simplest scenario of transferring records between Hana and Kafka. For the detail of this scenario and other more complex scenarios, refer to Examples#example.


#### Distributed Mode

In a production environment, it is suggested to run the Kafka Connector on [distributed mode](http://docs.confluent.io/3.0.0/connect/userguide.html#distributed-mode)


## Configuration

The `kafka connector for SAP Systems` provides a wide set of configuration options both for source & sink.

The full list of configuration options for `kafka connector for SAP Systems` is as follows:

* Sink

  * `topics` - This setting can be used to specify `a comma-separated list of topics`. Must not have spaces.

  * `auto.create` - This setting allows creation of a new table in SAP DBs if the table specified in `{topic}.table.name` does not exist. Should be a `Boolean`. Default is `false`.

  * `batch.size` - This setting can be used to specify the number of records that can be pushed into SAP DB table in a single flush. Should be an `Integer`. Default is `3000`.

  * `max.retries` - This setting can be used to specify the maximum no. of retries that can be made to re-establish the connection to SAP DB in case the connection is lost. Should be an `Integer`. Default is `10`.

  * `{topic}.table.name` - This setting allows specifying the SAP DBs table name where the data needs to be written to. Should be a `String`. Must be compatible to SAP DB Table name like `"SCHEMA"."TABLE"`.

  * `{topic}.table.type` - This is a DB specific configuration setting which allows creation of Row & Column tables if `auto.create` is set to true. Default value is `column`. And supported values are `column, row`.

  * `{topic}.pk.mode` - This setting can be used to specify the primary key mode required when `auto.create` is set to `true` & the table name specified in `{topic}.table.name` does not exist in SAP DB. Default is `none`. And supported values are `record_key, record_value`.

  * `{topic}.pk.fields` - This setting can be used to specify `a comma-separated list of primary key fields` when `{topic}.pk.mode` is set to `record_key` or `record_value`. Must not have spaces.

  * `{topic}.table.partition.mode` - This is a SapDB Sink specific configuration setting which determines the table partitioning in SAP DB. Default value is `none`. And supported values are `none, hash, round_robin`.

  * `{topic}.table.partition.count` - This is a SapDB Sink specific configuration setting which determines the number of partitions the table should have. Required when `auto.create` is set to `true` and table specified in `{topic}.table.name` does not exist in SAP DBs. Should be an `Integer`. Default value is `0`.

* Source

  * `topics` - This setting can be used to specify `a comma-separated list of topics`. Must not have spaces.

  * `mode` - This setting can be used to specify the mode in which data should be fetched from SAP DB table. Default is `bulk`. And supported values are `bulk, incrementing`.

  * `queryMode` - This setting can be used to specify the query mode in which data should be fetched from SAP DB table. Default is `table`. And supported values are `table, query ( to support sql queries )`. When 
using `queryMode: query` it is also required to have `query` parameter defined. This query parameter needs to be prepended by TopicName. If the `incrementing.column.name` property is used together to constrain the result, then it can be omitted from its where clause.

  * `{topic}.table.name` - This setting allows specifying the SAP DB table name where the data needs to be read from. Should be a `String`. Must be compatible to SAP DB Table name like `"SCHEMA"."TABLE"`.

  * `{topic}.query` - This setting allows specifying the query statement when `queryMode` is set to `query`. Should be a `String`.

  * `{topic}.poll.interval.ms` - This setting allows specifying the poll interval at which the data should be fetched from SAP DB table. Should be an `Integer`. Default value is `60000`.

  * `{topic}.incrementing.column.name` - In order to fetch data from a SAP DB table when `mode` is set to `incrementing`, an incremental ( or auto-incremental ) column needs to be provided. The type 
of the column can be numeric types such as `INTEGER`, `FLOAT`, `DECIMAL`, datetime types such as `DATE`, `TIME`, `TIMESTAMP`, and character types `VARCHAR`, `NVARCHAR` containing alpha-numeric characters. This considers SAP DB Timeseries tables also. Should be a valid column name ( respresented as a `String`) present in the table. See [data types in SAP HANA](https://help.sap.com/viewer/4fe29514fd584807ac9f2a04f6754767/LATEST/en-US/20a1569875191014b507cf392724b7eb.html)
 
  * `{topic}.partition.count` - This setting can be used to specify the no. of topic partitions that the Source connector can use to publish the data. Should be an `Integer`. Default value is `1`.


## Examples

Folder [`examples`](examples) includes some example scenarios. In addtion, the `unit tests` provide examples on every possible mode in which the connector can be configured.

## How to obtain support

We welcome comments, questions, and bug reports. Please [create an issue](https://github.com/SAP/kafka-connect-sap/issues) to obtain support.

## Contributing

Contributions are accepted by sending Pull Requests to this repo. Please do not forget to sign the [Contribution License Agreement](https://cla-assistant.io/SAP/kafka-connect-sap).

## Todos

Currently only SAP Hana is supported.


## License
Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
This file is licensed under the Apache Software License, v. 2 except as noted otherwise in the [LICENSE file](LICENSE).

