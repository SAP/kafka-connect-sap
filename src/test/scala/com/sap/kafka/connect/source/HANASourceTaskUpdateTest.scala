package com.sap.kafka.connect.source

import java.util

import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.MockJdbcClient
import com.sap.kafka.connect.config.hana.HANAParameters
import com.sap.kafka.connect.source.hana.HANASourceTask
import org.apache.kafka.connect.data.{Field, Schema, SchemaBuilder, Struct}
import org.scalatest.BeforeAndAfterEach

import scala.collection.JavaConversions._

object Field extends Enumeration {
  val VALUE, TIMESTAMP_VALUE,
  INCREMENTING_OFFSET, TIMESTAMP_OFFSET = Value
}

class HANASourceTaskUpdateTest extends HANASourceTaskTestBase
                                with BeforeAndAfterEach {
  protected var multiTableJdbcClient: MockJdbcClient = _
  protected var multiTableLoadTask: HANASourceTask = _
  protected var incrLoadJdbcClient: MockJdbcClient = _
  protected var incrLoadTask: HANASourceTask = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    incrLoadJdbcClient = new MockJdbcClient(
      HANAParameters.getConfig(singleTableConfigInIncrementalMode()))
    incrLoadTask = new HANASourceTask(time, incrLoadJdbcClient)
    multiTableJdbcClient = new MockJdbcClient(
      HANAParameters.getConfig(multiTableConfig()))
    multiTableLoadTask = new HANASourceTask(time, multiTableJdbcClient)

    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_BULK_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))),
      3000)
    incrLoadJdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA),
        new Field("name", 2, Schema.STRING_SCHEMA))), 3000)
    multiTableJdbcClient.createTable(Some("TEST"),
      FIRST_TABLE_NAME_FOR_MULTI_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))), 3000)
    multiTableJdbcClient.createTable(Some("TEST"),
      SECOND_TABLE_NAME_FOR_MULTI_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))), 3000)
  }

  override def afterAll(): Unit = {
    var connection = jdbcClient.getConnection
    var statement = connection.createStatement()
    statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_BULK_LOAD)

    connection = multiTableJdbcClient.getConnection
    statement = connection.createStatement()
    statement.execute("drop table " + FIRST_TABLE_NAME_FOR_MULTI_LOAD)
    statement.execute("drop table " + SECOND_TABLE_NAME_FOR_MULTI_LOAD)

    connection = incrLoadJdbcClient.getConnection
    statement = connection.createStatement()
    statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR_LOAD)
    super.afterAll()
  }

  override def beforeEach(): Unit = {

  }

  override def afterEach(): Unit = {
    task.stop()
    super.afterEach()
  }

  test("bulk periodic load") {
    val connection = jdbcClient.getConnection
    val stmt = connection.createStatement()
    stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_LOAD + " values(1)")

    val expectedSchema = SchemaBuilder.struct().name("expected schema")
                    .field("id", Schema.INT32_SCHEMA)
    task.start(singleTableConfig())
    var expectedData = new Struct(expectedSchema)
                        .put("id", 1)

    var records = task.poll()
    assert(records.size() === 1)

    records.toList.foreach(record => {
      compareSchema(expectedSchema, record.valueSchema())
      assert(record.value().isInstanceOf[Struct])
      compareData(expectedData, record.value().asInstanceOf[Struct],
        expectedSchema)
    })

    stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_LOAD + "values(2)")
    records = task.poll()
    //because this reads everything
    assert(records.size() === 2)

    var count = 1
    records.toList.foreach(record => {
      compareSchema(expectedSchema, record.valueSchema())
      assert(record.value().isInstanceOf[Struct])
      expectedData = new Struct(expectedSchema)
        .put("id", count)
      compareData(expectedData, record.value().asInstanceOf[Struct],
        expectedSchema)
      count = count + 1
    })
  }

  test("bulk periodic load on multiple tables") {
    val connection = multiTableJdbcClient.getConnection
    val stmt = connection.createStatement()
    stmt.execute("insert into " + FIRST_TABLE_NAME_FOR_MULTI_LOAD + "values(1)")

    val expectedSchemaForSingleTable = SchemaBuilder.struct()
      .name("expected schema for single table")
      .field("id", Schema.INT32_SCHEMA)

    stmt.execute("insert into " + SECOND_TABLE_NAME_FOR_MULTI_LOAD + "values(2)")

    val expectedSchemaForSecondTable = SchemaBuilder.struct()
      .name("expected schema for second table")
      .field("id", Schema.INT32_SCHEMA)

    multiTableLoadTask.start(multiTableConfig())

    val expectedDataForFirstTable = new Struct(expectedSchemaForSingleTable)
                                      .put("id", 1)

    val expectedDataForSecondTable = new Struct(expectedSchemaForSecondTable)
                                        .put("id", 2)

    val records = multiTableLoadTask.poll()

    assert(records.size() === 1)

    records.foreach(record => {
      if (record.topic() == TOPIC) {
        compareSchema(expectedSchemaForSingleTable, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedDataForFirstTable, record.value().asInstanceOf[Struct],
          expectedSchemaForSingleTable)
      } else if (record.topic() == SECOND_TOPIC) {
        compareSchema(expectedSchemaForSecondTable, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedDataForSecondTable, record.value().asInstanceOf[Struct],
          expectedSchemaForSecondTable)
      }
    })
  }

  test("incremental column load test") {
    val connection = incrLoadJdbcClient.getConnection
    val stmt = connection.createStatement()
    stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_LOAD + "values(1, 'Lukas')")

    val expectedSchema = SchemaBuilder.struct().name("expected schema")
                          .field("id", Schema.INT32_SCHEMA)
                          .field("name", Schema.STRING_SCHEMA)
    incrLoadTask.initialize(taskContext)
    incrLoadTask.start(singleTableConfigInIncrementalMode())
    var expectedData = new Struct(expectedSchema)
                        .put("id", 1)
                        .put("name", "Lukas")

    var records = incrLoadTask.poll()
    assert(records.size() === 1)

    records.toList.foreach(record => {
      compareSchema(expectedSchema, record.valueSchema())
      assert(record.value().isInstanceOf[Struct])
      compareData(expectedData, record.value().asInstanceOf[Struct],
        expectedSchema)
    })

    stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_LOAD + "values(2, 'Lukas')")
    records = incrLoadTask.poll()
    // because this only takes the delta
    assert(records.size() === 1)

    records.toList.foreach(record => {
      compareSchema(expectedSchema, record.valueSchema())
      assert(record.value().isInstanceOf[Struct])

      expectedData = new Struct(expectedSchema)
                      .put("id", 2)
                      .put("name", "Lukas")
      compareData(expectedData, record.value().asInstanceOf[Struct],
        expectedSchema)
    })
  }

  private def compareSchema(expectedSchema: Schema, actualSchema: Schema): Unit = {
    val expectedFields = expectedSchema.fields().toList
    val actualFields = actualSchema.fields().toList

    assert(expectedFields.size === actualFields.size)
    var count = 0
    expectedFields.foreach(field => {
      assert(field.name() === actualFields(count).name())
      assert(field.schema() === actualFields(count).schema())
      count = count + 1
    })
  }

  private def compareData(expectedData: Struct, actualData: Struct,
                          schema: Schema): Unit = {
    val fields = schema.fields()

    fields.foreach(field => {
      assert(expectedData.get(field.name()) ===
        actualData.get(field.name()))
    })
  }

  protected def singleTableConfigInIncrementalMode():
  java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    val tmpDir = System.getProperty("java.io.tmpdir")
    props.put("connection.url", "jdbc:h2:file:" + tmpDir + "test;" +
      "INIT=CREATE SCHEMA IF NOT EXISTS TEST")
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "incrementing")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", SINGLE_TABLE_NAME_FOR_INCR_LOAD)
    props.put(s"$TOPIC.partition.count", "5")
    props.put(s"$TOPIC.poll.interval.ms", "60000")
    props.put(s"$TOPIC.incrementing.column.name", "id")

    props
  }
}