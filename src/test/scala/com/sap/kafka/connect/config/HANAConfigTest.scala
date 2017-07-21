package com.sap.kafka.connect.config

import java.util

import com.sap.kafka.client.hana.HANAConfigMissingException
import com.sap.kafka.connect.config.hana.HANAParameters
import org.scalatest.FunSuite

class HANAConfigTest extends FunSuite {
  test("minimum config for sink") {
    val props = new util.HashMap[String, String]()
    props.put("name", "test-sink")
    props.put("connector.class", "com.sap.kafka.connect.sink.HANASinkConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")
    props.put("test-topic.table.name", "\"TEST\".\"TABLE\"")

    val config = HANAParameters.getConfig(props)

    assert(config.connectionUrl === props.get("connection.url"))
    assert(config.connectionUser === props.get("connection.user"))
    assert(config.connectionPassword === props.get("connection.password"))
    assert(config.topics.mkString(",") === props.get("topics"))

    val topicProperties = config.topicProperties("test-topic")
    assert(topicProperties.get("table.name").get === props.get("test-topic.table.name"))
  }

  test("minimum config for source") {
    val props = new util.HashMap[String, String]()
    props.put("name", "test-source")
    props.put("connector.class", "com.sap.kafka.connect.source.HANASourceConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")
    props.put("test-topic.table.name", "\"TEST\".\"TABLE\"")

    val config = HANAParameters.getConfig(props)

    assert(config.connectionUrl === props.get("connection.url"))
    assert(config.connectionUser === props.get("connection.user"))
    assert(config.connectionPassword === props.get("connection.password"))
    assert(config.topics.mkString(",") === props.get("topics"))

    val topicProperties = config.topicProperties("test-topic")
    assert(topicProperties.get("table.name").get === props.get("test-topic.table.name"))
  }

  test("missing mandatory properties") {
    var props = new util.HashMap[String, String]()
    props.put("name", "test-source")
    props.put("connector.class", "com.sap.kafka.connect.source.HANASourceConnector")
    props.put("tasks.max", "1")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")
    props.put("test-topic.table.name", "\"TEST\".\"TABLE\"")

    // connection.url missing
    intercept[HANAConfigMissingException] {
      HANAParameters.getConfig(props)
    }

    props = new util.HashMap[String, String]()
    props.put("name", "test-source")
    props.put("connector.class", "com.sap.kafka.connect.source.HANASourceConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")
    props.put("test-topic.table.name", "\"TEST\".\"TABLE\"")

    // one of connection.user or connection.password missing
    intercept[HANAConfigMissingException] {
      HANAParameters.getConfig(props)
    }

    props = new util.HashMap[String, String]()
    props.put("name", "test-source")
    props.put("connector.class", "com.sap.kafka.connect.source.HANASourceConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("test-topic.table.name", "\"TEST\".\"TABLE\"")

    // topics is missing
    intercept[HANAConfigMissingException] {
      HANAParameters.getConfig(props)
    }
  }

  test("missing mandatory topic properties for sink") {
    val props = new util.HashMap[String, String]()
    props.put("name", "test-sink")
    props.put("connector.class", "com.sap.kafka.connect.sink.HANASinkConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")

    val config = HANAParameters.getConfig(props)

    intercept[HANAConfigMissingException] {
      config.topicProperties("test-topic").get("table.name")
    }
  }

  test("missing mandatory topic properties for source") {
    val props = new util.HashMap[String, String]()
    props.put("name", "test-source")
    props.put("connector.class", "com.sap.kafka.connect.source.HANASourceConnector")
    props.put("tasks.max", "1")
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "test-topic")

    val config = HANAParameters.getConfig(props)

    intercept[HANAConfigMissingException] {
      config.topicProperties("test-topic").get("table.name")
    }
  }
}