# Kafka Connectors for SAP

Kafka Connect SAP is a set of connectors, using the Apache Kafka Connect framework for reliably connecting Kafka with SAP systems

Table of contents
-------------

* [Install](#install)
* [QuickStart](#quickstart)
* [Configuration](#configuration)
* [Examples](#examples)


## Install

To install the connector from source, use the following command.

```
mvn clean install -DskipTests
```

**Include the SAP HANA Jdbc Jar**

- Follow the steps in [this](http://help.sap.com/saphelp_hanaplatform/helpdata/en/ff/15928cf5594d78b841fbbe649f04b4/frameset.htm) guide to access the SAP HANA Jdbc jar.
- Place it in the same directory as the `Kafka Connector` jar or under the `CLASSPATH` directory.

## QuickStart

For getting started with this connector, the following steps need to be completed.

- Create the config file for sink named `kafka-connect-hana-sink.properties`.

```
name=test-sink
connector.class=com.sap.kafka.connect.sink.hana.HANASinkConnector
tasks.max=1
topics=test_topic
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
auto.create=true
schema.registry.url=<schema registry url>
test_topic.table.name="SYSTEM"."DUMMY_TABLE"
```

- Start the kafka-connect-hana sink connector using the following command.

```
./bin/connect-standalone ./etc/schema-registry/connect-avro-standalone.properties ./etc/kafka/kafka-connect-hana-sink.properties
```

- Create the config file for source named `kafka-connect-hana-source.properties`.

```
name=kafka-connect-source
connector.class=com.sap.kafka.connect.source.hana.HANASourceConnector
tasks.max=1
topics=kafka_source_1,kafka_source_2
connection.url=jdbc:sap://<url>/
connection.user=<username>
connection.password=<password>
kafka_source_1.table.name="SYSTEM"."com.sap.test::hello"
```

- - Start the kafka-connect-hana source connector using the following command.

```
./bin/connect-standalone ./etc/schema-registry/connect-avro-standalone.properties ./etc/kafka/kafka-connect-hana-source.properties
```

#### Distributed Mode

In a production environment, it is suggested to run the Kafka Connector on [distributed mode](http://docs.confluent.io/3.0.0/connect/userguide.html#distributed-mode)


## Configuration

The `kafka connector for SAP Hana` provides a wide set of configuration options both for source & sink.

The full list of configuration options for `kafka connector for SAP Hana` is as follows:

* Sink

  * `topics` - This setting can be used to specify `a comma-separated list of topics`. Must not have spaces.

  * `auto.create` - This setting allows creation of a new table in SAP Hana if the table specified in `{topic}.table.name` does not exist. Should be a `Boolean`. Default is `false`.

  * `batch.size` - This setting can be used to specify the number of records that can be pushed into HANA DB table in a single flush. Should be an `Integer`. Default is `3000`.

  * `max.retries` - This setting can be used to specify the maximum no. of retries that can be made to re-establish the connection to SAP HANA in case the connection is lost. Should be an `Integer`. Default is `10`.

  * `{topic}.table.name` - This setting allows specifying the SAP Hana table name where the data needs to be written to. Should be a `String`. Must be compatible to SAP HANA Table name like `"SCHEMA"."TABLE"`.

  * `{topic}.table.type` - This is a HANA specific configuration setting which allows creation of Row & Column tables if `auto.create` is set to true. Default value is `column`. And supported values are `column, row`.

  * `{topic}.pk.mode` - This setting can be used to specify the primary key mode required when `auto.create` is set to `true` & the table name specified in `{topic}.table.name` does not exist in SAP HANA. Default is `none`. And supported values are `record_key, record_value`.

  * `{topic}.pk.fields` - This setting can be used to specify `a comma-separated list of primary key fields` when `{topic}.pk.mode` is set to `record_key` or `record_value`. Must not have spaces.

  * `{topic}.table.partition.mode` - This is a HANA Sink specific configuration setting which determines the table partitioning in HANA. Default value is `none`. And supported values are `none, hash, round_robin`.

  * `{topic}.table.partition.count` - This is a HANA Sink specific configuration setting which determines the number of partitions the table should have. Required when `auto.create` is set to `true` and table specified in `{topic}.table.name` does not exist in SAP Hana. Should be an `Integer`. Default value is `0`.

- Source

  * `topics` - This setting can be used to specify `a comma-separated list of topics`. Must not have spaces.

  * `mode` - This setting can be used to specify the mode in which data should be fetched from SAP HANA table. Default is `bulk`. And supported values are `bulk, incrementing`.

  * `queryMode` - This setting can be used to specify the query mode in which data should be fetched from SAP HANA table. Default is `table`. And supported values are `table, query ( to support sql queries )`.

  * `{topic}.table.name` - This setting allows specifying the SAP Hana table name where the data needs to be written to. Should be a `String`. Must be compatible to SAP HANA Table name like `"SCHEMA"."TABLE"`.

  * `{topic}.poll.interval.ms` - This setting allows specifying the poll interval at which the data should be fetched from SAP HANA table. Should be an `Integer`. Default value is `60000`.

  * `{topic}.incrementing.column.name` - In order to fetch data from a SAP Hana table when `mode` is set to `incrementing`, an incremental ( or auto-incremental ) column needs to be provided. The type 
of the column can be `Int, Float, Decimal, Timestamp`. This considers SAP HANA Timeseries tables also. Should be a valid clumn name ( respresented as a `String`) present in the table.
 
  * `{topic}.partition.count` - This setting can be used to specify the no. of topic partitions that the Source connector can use to publish the data. Should be an `Integer`. Default value is `1`.


#### Default Configurations

- [Sink Connector Config](config/kafka-connect-hana-sink.properties)
- [Source Connector Config](config/kafka-connect-hana-source.properties)

## Examples

The `unit tests` provide examples on every possible mode in which the connector can be configured.



