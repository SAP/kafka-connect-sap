[![Java CI with Maven](https://github.com/SAP/kafka-connect-sap/actions/workflows/maven.yml/badge.svg)](https://github.com/SAP/kafka-connect-sap/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![REUSE status](https://api.reuse.software/badge/github.com/SAP/kafka-connect-sap)](https://api.reuse.software/info/github.com/SAP/kafka-connect-sap)

# Kafka Connectors for SAP

Kafka Connect SAP is a generic set of connectors, using the Apache Kafka Connect framework for reliably connecting Kafka with SAP systems

Table of contents
-------------

* [Install](#install)
* [QuickStart](#quickstart)
* [Configuration](#configuration)
* [Examples](#examples)
* [Compatibility](#compatibility)

## Install

To install the connector from source, 

Clone this repository to your local desktop, and then bring up a command prompt in the directory.

and use the following command.

```
mvn clean install -DskipTests
```

which should produce the Kafka Connector jar file `kafka-connector-hana_m-n.jar` in the `modules/scala_m/target` folder, where `m` corresponds to Scala binary version and `n` corresponds to the connector version.

**Include the Jdbc Jar**

#####   For SAP Hana

> Please refer to [SAP Developer License Agreement](https://tools.hana.ondemand.com/developer-license-3_1.txt) for the use of the driver jar.

- Follow the steps in [SAP HANA Client Interface Programming Reference](https://help.sap.com/viewer/f1b440ded6144a54ada97ff95dac7adf/LATEST/en-US/434e2962074540e18c802fd478de86d6.html) guide to access the SAP HANA Jdbc jar.
- The maven coordinate of the driver is `com.sap.cloud.db.jdbc:ngdbc:x.x.x` and the drivers are available at the central maven repository [https://search.maven.org/artifact/com.sap.cloud.db.jdbc/ngdbc](https://search.maven.org/artifact/com.sap.cloud.db.jdbc/ngdbc).

## QuickStart

There are some examples that can be executed by following the instructions. For the detail of these examples, refer to [Examples](#examples).


#### Running Kafka Connect

The demo examples included in [Examples](#examples) use Kafka Connect running in different environments such as standalone and distributed modes. For general information on how to run Kafka Connect, refer to [Kafka Connect documentation](https://kafka.apache.org/documentation/#connect_running)


## Configuration

The `kafka connector for SAP Systems` provides a wide set of configuration options both for source & sink.

The full list of configuration options for `kafka connector for SAP Systems` is as follows:

* Sink

    * `topics` - This setting can be used to specify `a comma-separated list of topics`. Must not have spaces.

    * `auto.create` - This setting allows the creation of a new table in SAP DBs if the table specified in `{topic}.table.name` does not exist. Should be a `Boolean`. Default is `false`.

    * `auto.evolve` - This setting allows the evolution of the table schema with some restriction, namely when the record contains additional nullable fields that are not present previously, the corresponding columns will be added. In contrast, when the record contains less fields, the table schema will not be changed. Should be a `Boolean`. Default is `false`.

    * `batch.size` - This setting can be used to specify the number of records that can be pushed into SAP DB table in a single flush. Should be an `Integer`. Default is `3000`.

    * `max.retries` - This setting can be used to specify the maximum no. of retries that can be made to re-establish the connection to SAP DB in case the connection is lost. Should be an `Integer`. Default is `10`.

    * `{topic}.table.name` - This setting allows specifying the SAP DBs table name where the data needs to be written to. Should be a `String`. Must be compatible to SAP DB Table name like `"SCHEMA"."TABLE"`.

    * `{topic}.table.type` - This is a DB specific configuration setting which allows creation of Row & Column tables if `auto.create` is set to true. Default value is `column`. And supported values are `column, row`.
  
    * `{topic}.insert.mode` - This setting can be used to specify one of the available insertion modes `insert` and `upsert`. Default is `insert`.

    * `{topic}.delete.enabled` - This setting can be used to allow the deletion of the record when its corresponding tombstone record is received by the connector. Default is `false`.

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

    * `numeric.mapping` - This setting can be used to control whether the DECIMAL column types are mapped to the default decimal type or one of the primitive types. The supported values are `none`, `best_fit`, and `best_fit_eager_double`. The default value is `none`.


* Transformations

    * `EscapeFieldNameCharacters` - This SMT translates the field names by escaping certain characters of the names to make them valid in the naming scheme of the target. Each escaped character is represented in a sequence of UTF-8 bytes, each in form `<esc><xx>`, where `<esc>` is the specified escape character and `<xx>` is the hexiadecimal value of the byte.
        * `type` - `com.sap.kafka.connect.transforms.EscapeFieldNameCharacters$Key` and `com.sap.kafka.connect.transforms.EscapeFieldNameCharacters$Value`
        * `escape.char` - The escape character to be used.
        * `valid.chars.default` - This value specifies the valid character set used in escaping those characters outside of the specified set. When this value is not set, the names are unescaped.
        * `valid.chars.first` - This value optinally specifies the valid character set for the first character if this difers from the rest.

## Examples

Folder [`examples`](examples) includes some example scenarios. In addtion, the `unit tests` provide examples on every possible mode in which the connector can be configured.

## How to obtain support

We welcome comments, questions, and bug reports. Please [create an issue](https://github.com/SAP/kafka-connect-sap/issues) to obtain support.

## Contributing

Contributions are accepted by sending Pull Requests to this repo.

### Developer Certificate of Origin (DCO)
Due to legal reasons, contributors will be asked to accept a DCO when they create the first pull request to this project. This happens in an automated fashion during the submission process. SAP uses [the standard DCO text of the Linux Foundation](https://developercertificate.org/).


## Compatibility

[compatiblity-matrix.txt](./compatiblity-matrix.txt)

## Todos

Currently only SAP Hana is supported.

## License

Copyright (c) 2015-2022 SAP SE or an SAP affiliate company and kafka-connect-sap contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/SAP/kafka-connect-sap).
