package com.sap.kafka.connect.sink

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util

import com.sap.kafka.connect.MockJdbcClient
import com.sap.kafka.connect.config.hana.HANAParameters
import com.sap.kafka.connect.sink.hana.HANASinkTask
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.sink.{SinkRecord, SinkTaskContext}
import org.scalatest.FunSuite
import org.mockito.Mockito.mock

import scala.math.BigDecimal.RoundingMode

class AvroLogicalTypesTest extends FunSuite {

  test("put propagates to DB with schema containing date fields") {
    val schema = SchemaBuilder.struct()
      .field("date_field", Date.builder().build())
      .build()

    val props = new util.HashMap[String, String]()
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "testTopic")
    props.put("testTopic.table.name", "\"TEST\".\"DATE_TABLE\"")
    props.put("testTopic.table.type", "row")
    props.put("auto.create", "true")
    props.put("testTopic.pk.mode", "record_value")
    props.put("testTopic.pk.fields", "date_field")

    val task = new HANASinkTask()

    task.initialize(mock(classOf[SinkTaskContext]))
    task.start(props)

    val config = HANAParameters.getConfig(props)
    task.hanaClient = new MockJdbcClient(config)
    task.initWriter(config)

    // when using an Avro Schema. use custom parameters for kafka connect like this.
    // {
    //  "name": "date",
    //  "type": {
    //    "type": "int",
    //    "connect.version": 1,
    //    "connect.name": "org.apache.kafka.connect.data.Date"
    //  }
    // }
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val expectedDateField = "2013-10-16"
    val date = simpleDateFormat.parse(expectedDateField)


    val struct = new Struct(schema).put("date_field", date)

    task.put(util.Collections.singleton(
      new SinkRecord("testTopic", 1, null, null, schema, struct, 42)
    ))

    val rs = task.hanaClient.executeQuery(schema, "select * from \"TEST\".\"DATE_TABLE\"",
    -1, -1)
    val structs = rs.get
    assert(structs.size === 1)
    val head = structs.head

    val actualDateField = head.get("date_field").toString
    assert(expectedDateField === actualDateField)
  }

  test("put propagates to DB with schema containing time fields") {
    val schema = SchemaBuilder.struct()
      .field("time_field", Time.builder().build())
      .build()

    val props = new util.HashMap[String, String]()
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "testTopic")
    props.put("testTopic.table.name", "\"TEST\".\"TIME_TABLE\"")
    props.put("testTopic.table.type", "row")
    props.put("auto.create", "true")
    props.put("testTopic.pk.mode", "record_value")
    props.put("testTopic.pk.fields", "time_field")

    val task = new HANASinkTask()

    task.initialize(mock(classOf[SinkTaskContext]))
    task.start(props)

    val config = HANAParameters.getConfig(props)
    task.hanaClient = new MockJdbcClient(config)
    task.initWriter(config)

    // when using an Avro Schema. use custom parameters for kafka connect like this.
    // {
    //  "name": "time",
    //  "type": {
    //    "type": "int",
    //    "connect.version": 1,
    //    "connect.name": "org.apache.kafka.connect.data.Time"
    //  }
    // }
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'")
    val expectedDateField = "2013-10-16T02:02:02Z"
    val expectedTimeField = "02:02:02"
    val date = simpleDateFormat.parse(expectedDateField)


    val struct = new Struct(schema).put("time_field", date)

    task.put(util.Collections.singleton(
      new SinkRecord("testTopic", 1, null, null, schema, struct, 42)
    ))

    val rs = task.hanaClient.executeQuery(schema, "select * from \"TEST\".\"TIME_TABLE\"",
      -1, -1)
    val structs = rs.get
    assert(structs.size === 1)
    val head = structs.head

    val actualDateField = head.get("time_field").toString
    assert(expectedTimeField === actualDateField)
  }

  test("put propagates to DB with schema containing timestamp fields") {
    val schema = SchemaBuilder.struct()
      .field("timestamp_field", Timestamp.builder().build())
      .build()

    val props = new util.HashMap[String, String]()
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "testTopic")
    props.put("testTopic.table.name", "\"TEST\".\"TIMESTAMP_TABLE\"")
    props.put("testTopic.table.type", "row")
    props.put("auto.create", "true")
    props.put("testTopic.pk.mode", "record_value")
    props.put("testTopic.pk.fields", "timestamp_field")

    val task = new HANASinkTask()

    task.initialize(mock(classOf[SinkTaskContext]))
    task.start(props)

    val config = HANAParameters.getConfig(props)
    task.hanaClient = new MockJdbcClient(config)
    task.initWriter(config)

    // when using an Avro Schema. use custom parameters for kafka connect like this.
    // {
    //  "name": "timestamp",
    //  "type": {
    //    "type": "long",
    //    "connect.version": 1,
    //    "connect.name": "org.apache.kafka.connect.data.Timestamp"
    //  }
    // }
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'")
    val expectedDateField = "2013-10-16T02:02:02.002Z"
    val expectedTimestampField = "2013-10-16 02:02:02.0"
    val date = simpleDateFormat.parse(expectedDateField)


    val struct = new Struct(schema).put("timestamp_field", date)

    task.put(util.Collections.singleton(
      new SinkRecord("testTopic", 1, null, null, schema, struct, 42)
    ))

    val rs = task.hanaClient.executeQuery(schema, "select * from \"TEST\".\"TIMESTAMP_TABLE\"",
      -1, -1)
    val structs = rs.get
    assert(structs.size === 1)
    val head = structs.head

    val actualDateField = head.get("timestamp_field").toString
    assert(expectedTimestampField === actualDateField)
  }

  test("put propagates to DB with schema containing decimal fields") {
    val schema = SchemaBuilder.struct()
      .field("decimal_field", Decimal.builder(2).build())
      .build()

    val props = new util.HashMap[String, String]()
    props.put("connection.url", "jdbc:h2:mem:test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("topics", "testTopic")
    props.put("testTopic.table.name", "\"TEST\".\"DECIMAL_TABLE\"")
    props.put("testTopic.table.type", "row")
    props.put("auto.create", "true")
    props.put("testTopic.pk.mode", "record_value")
    props.put("testTopic.pk.fields", "decimal_field")

    val task = new HANASinkTask()

    task.initialize(mock(classOf[SinkTaskContext]))
    task.start(props)

    val config = HANAParameters.getConfig(props)
    task.hanaClient = new MockJdbcClient(config)
    task.initWriter(config)

    // when using an Avro Schema. use custom parameters for kafka connect like this.
    // {
    //  "name": "decimal",
    //  "type": {
    //    "type": "bytes",
    //    "connect.version": 1,
    //    "connect.parameters": {
    //      "scale": "2"
    //    },
    //    "connect.name": "org.apache.kafka.connect.data.Decimal"
    //  }
    // }
    val expectedDecimalField: java.math.BigDecimal =
      new BigDecimal(2.217).setScale(2, RoundingMode.FLOOR).bigDecimal


    val struct = new Struct(schema).put("decimal_field", expectedDecimalField)

    task.put(util.Collections.singleton(
      new SinkRecord("testTopic", 1, null, null, schema, struct, 42)
    ))

    val rs = task.hanaClient.executeQuery(schema, "select * from \"TEST\".\"DECIMAL_TABLE\"",
      -1, -1)
    val structs = rs.get
    assert(structs.size === 1)
    val head = structs.head

    val actualDateField = head.get("decimal_field").toString
    assert(expectedDecimalField.toString === actualDateField)
  }
}