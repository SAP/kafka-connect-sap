package com.sap.kafka.connect.source

import java.util
import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.source.hana.{HANASourceConnector, HANASourceTask}
import com.sap.kafka.connect.source.querier.TimestampIncrementingOffset
import org.apache.kafka.connect.data.{Field, Schema, SchemaBuilder, Struct, Timestamp}
import org.apache.kafka.connect.source.SourceRecord
import org.scalatest.BeforeAndAfterEach

object Field extends Enumeration {
  val VALUE, TIMESTAMP_VALUE,
  INCREMENTING_OFFSET, TIMESTAMP_OFFSET = Value
}

class HANASourceTaskUpdateTest extends HANASourceTaskTestBase
                                with BeforeAndAfterEach {
  protected var multiTableLoadTask: HANASourceTask = _
  protected var queryLoadTask: HANASourceTask = _
  protected var incrLoadTask: HANASourceTask = _
  protected var incr2LoadTask: HANASourceTask = _
  protected var incr3LoadTask: HANASourceTask = _
  protected var incrQueryLoadTask: HANASourceTask = _
  protected var maxrowsLoadTask: HANASourceTask = _
  protected var maxrowsIncrLoadTask: HANASourceTask = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    queryLoadTask = new HANASourceTask(time, jdbcClient)
    incrLoadTask = new HANASourceTask(time, jdbcClient)
    incr2LoadTask = new HANASourceTask(time, jdbcClient)
    incr3LoadTask = new HANASourceTask(time, jdbcClient)
    incrQueryLoadTask = new HANASourceTask(time, jdbcClient)
    multiTableLoadTask = new HANASourceTask(time, jdbcClient)
    maxrowsLoadTask = new HANASourceTask(time, jdbcClient)
    maxrowsIncrLoadTask = new HANASourceTask(time, jdbcClient)

    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_BULK_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))),
      3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))),
      3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))),
      3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA),
        new Field("name", 2, Schema.STRING_SCHEMA))), 3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR2_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.STRING_SCHEMA),
        new Field("name", 2, Schema.STRING_SCHEMA))), 3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR3_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("ts", 1, Timestamp.SCHEMA),
        new Field("name", 2, Schema.STRING_SCHEMA))), 3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA),
        new Field("name", 2, Schema.STRING_SCHEMA))), 3000)
    jdbcClient.createTable(Some("TEST"),
      SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))),
      3000)
    jdbcClient.createTable(Some("TEST"),
      FIRST_TABLE_NAME_FOR_MULTI_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))), 3000)
    jdbcClient.createTable(Some("TEST"),
      SECOND_TABLE_NAME_FOR_MULTI_LOAD.split("\\.")(1).replace("\"", ""),
      MetaSchema(null, Seq(new Field("id", 1, Schema.INT32_SCHEMA))), 3000)
  }

  override def afterAll(): Unit = {
    var connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val statement = connection.createStatement()
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_BULK_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD)
      statement.execute("drop table " + FIRST_TABLE_NAME_FOR_MULTI_LOAD)
      statement.execute("drop table " + SECOND_TABLE_NAME_FOR_MULTI_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR2_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR3_LOAD)
      statement.execute("drop table " + SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD)
    } finally {
      connection.close()
    }

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

    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_LOAD + " values(1)")

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableConfig())
      task.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)

      var records = task.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_LOAD + "values(2)")
      records = task.poll()
      //return after waking up to allow the status to be checked before actuall polling
      assert(records == null)
      records = task.poll()
      //because this reads everything
      assert(records.size() === 2)

      var count = 1
      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        expectedData = new Struct(expectedSchema)
          .put("id", count)
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
        count = count + 1
      })

      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("bulk periodic load on multiple tables") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + FIRST_TABLE_NAME_FOR_MULTI_LOAD + "values(1)")

      val expectedSchemaForSingleTable = SchemaBuilder.struct()
        .name("expected schema for single table")
        .field("id", Schema.INT32_SCHEMA)

      stmt.execute("insert into " + SECOND_TABLE_NAME_FOR_MULTI_LOAD + "values(2)")

      val expectedSchemaForSecondTable = SchemaBuilder.struct()
        .name("expected schema for second table")
        .field("id", Schema.INT32_SCHEMA)

      val connector = new HANASourceConnector
      connector.start(multiTableConfig())
      multiTableLoadTask.start(connector.taskConfigs(1).get(0))

      val expectedDataForFirstTable = new Struct(expectedSchemaForSingleTable)
        .put("id", 1)

      val expectedDataForSecondTable = new Struct(expectedSchemaForSecondTable)
        .put("id", 2)

      val records = multiTableLoadTask.poll()

      assert(records.size() === 1)

      records.forEach(record => {
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
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("bulk periodic query load") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD + " values(1)")

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableQueryConfig())
      queryLoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)

      var records = queryLoadTask.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD + "values(2)")
      records = queryLoadTask.poll()
      assert(records == null)
      records = queryLoadTask.poll()
      //because this reads everything
      assert(records.size() === 2)

      var count = 1
      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        expectedData = new Struct(expectedSchema)
          .put("id", count)
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
        count = count + 1
      })
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("incremental int-column load test") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_LOAD + "values(1, 'Lukas')")

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
        .field("name", Schema.STRING_SCHEMA)
      incrLoadTask.initialize(taskContext)
      val connector = new HANASourceConnector
      connector.start(singleTableConfigInIncrementalMode(SINGLE_TABLE_NAME_FOR_INCR_LOAD, "id"))
      incrLoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)
        .put("name", "Lukas")

      var records = incrLoadTask.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_LOAD + "values(2, 'Lukas')")
      records = incrLoadTask.poll()
      assert(records == null)
      records = incrLoadTask.poll()
      // because this only takes the delta
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])

        expectedData = new Struct(expectedSchema)
          .put("id", 2)
          .put("name", "Lukas")
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("incremental string-column load test") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR2_LOAD + "values('1', 'Lukas')")

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.STRING_SCHEMA)
        .field("name", Schema.STRING_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableConfigInIncrementalMode(SINGLE_TABLE_NAME_FOR_INCR2_LOAD, "id"))
      incr2LoadTask.initialize(taskContext)
      incr2LoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", "1")
        .put("name", "Lukas")

      var records = incr2LoadTask.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR2_LOAD + "values('2', 'Lukas')")
      records = incr2LoadTask.poll()
      assert(records == null)
      records = incr2LoadTask.poll()
      // because this only takes the delta
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])

        expectedData = new Struct(expectedSchema)
          .put("id", "2")
          .put("name", "Lukas")
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("incremental timestamp-column load test") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      val ts1 = new java.sql.Timestamp(1000l)
      val ts2 = new java.sql.Timestamp(2000l)
      val ts1str = TimestampIncrementingOffset.UTC_DATETIME_FORMAT.format(ts1)
      val ts2str = TimestampIncrementingOffset.UTC_DATETIME_FORMAT.format(ts2)
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR3_LOAD +
        "values('%s', 'Lukas')".format(ts1str))

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("ts", Timestamp.builder().build())
        .field("name", Schema.STRING_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableConfigInIncrementalMode(SINGLE_TABLE_NAME_FOR_INCR3_LOAD, "ts"))
      incr3LoadTask.initialize(taskContext)
      incr3LoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("ts", ts1)
        .put("name", "Lukas")

      var records = incr3LoadTask.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR3_LOAD +
        "values('%s', 'Lukas')".format(ts2str))
      records = incr3LoadTask.poll()
      assert(records == null)
      records = incr3LoadTask.poll()
      // because this only takes the delta
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])

        expectedData = new Struct(expectedSchema)
          .put("ts", ts2)
          .put("name", "Lukas")
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("incremental column query load test") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD + "values(1, 'Lukas')")

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
        .field("name", Schema.STRING_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableConfigInIncrementalQueryMode())
      incrQueryLoadTask.initialize(taskContext)
      incrQueryLoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)
        .put("name", "Lukas")

      var records = incrQueryLoadTask.poll()
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })

      stmt.execute("insert into " + SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD + "values(2, 'Lukas')")
      records = incrQueryLoadTask.poll()
      assert(records == null)
      records = incrQueryLoadTask.poll()
      // because this only takes the delta
      assert(records.size() === 1)

      records.forEach(record => {
        compareSchema(expectedSchema, record.valueSchema())
        assert(record.value().isInstanceOf[Struct])

        expectedData = new Struct(expectedSchema)
          .put("id", 2)
          .put("name", "Lukas")
        compareData(expectedData, record.value().asInstanceOf[Struct],
          expectedSchema)
      })
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("bulk periodic load with iterations by batch.max.row") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      val sqlstr = "insert into %s values(%d)"
      for (i <- 1 to 5) {
        stmt.execute(sqlstr.format(SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD, i))
      }

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableMaxRowsConfig("2"))
      maxrowsLoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)

      // batch.max.rows 2 will require 3 polls to load 5 rows
      for(i <- 1 to 3){
        var records = maxrowsLoadTask.poll()
        assert(records.size() === (i match {
          case 3 => 1
          case _ => 2
        }))
        verifyRecords(i-1, 2, records, expectedSchema)
      }

      stmt.execute(sqlstr.format(SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD, 6))
      for(i <- 1 to 3) {
        var records = maxrowsLoadTask.poll()
        if (i == 1) {
          //needs to reset the time for the initial poll
          assert(records == null)
          records = maxrowsLoadTask.poll()
        }
        assert(records.size() === 2)
        verifyRecords(i-1, 2, records, expectedSchema)
      }
      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  test("incremental column load with iterations by batch.max.row") {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      val sqlstr = "insert into %s values(%d)"
      for (i <- 1 to 5) {
        stmt.execute(sqlstr.format(SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD, i))
      }

      val expectedSchema = SchemaBuilder.struct().name("expected schema")
        .field("id", Schema.INT32_SCHEMA)
      val connector = new HANASourceConnector
      connector.start(singleTableMaxRowsConfigInIncrementalMode("2"))
      maxrowsIncrLoadTask.initialize(taskContext)
      maxrowsIncrLoadTask.start(connector.taskConfigs(1).get(0))
      var expectedData = new Struct(expectedSchema)
        .put("id", 1)

      // batch.max.rows 2 will require 3 polls to load 5 rows
      for(i <- 1 to 3){
        var records = maxrowsIncrLoadTask.poll()
        assert(records.size() === (i match {
          case 3 => 1
          case _ => 2
        }))
        verifyRecords(i-1, 2, records, expectedSchema)
      }

      stmt.execute(sqlstr.format(SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD, 6))
      var records = maxrowsIncrLoadTask.poll()
      assert(records == null)
      records = maxrowsIncrLoadTask.poll()
      assert(records.size() === 1)
      assert(maxrowsIncrLoadTask.poll() === null)

      task.stop()
      connector.stop()
    } finally {
      connection.close()
    }
  }

  private def compareSchema(expectedSchema: Schema, actualSchema: Schema): Unit = {
    val expectedFields = expectedSchema.fields()
    val actualFields = actualSchema.fields()

    assert(expectedFields.size === actualFields.size)
    var count = 0
    expectedFields.forEach(field => {
      assert(field.name() === actualFields.get(count).name())
      assert(field.schema() === actualFields.get(count).schema())
      count = count + 1
    })
  }

  private def compareData(expectedData: Struct, actualData: Struct,
                          schema: Schema): Unit = {
    val fields = schema.fields()

    fields.forEach(field => {
      assert(expectedData.get(field.name()) ===
        actualData.get(field.name()))
    })
  }

  private def verifyRecords(cycle : Int, size : Int, records : util.List[SourceRecord], expectedSchema : SchemaBuilder): Unit = {
    var index = 1
    records.forEach(record => {
      compareSchema(expectedSchema, record.valueSchema())
      assert(record.value().isInstanceOf[Struct])
      var expectedData = new Struct(expectedSchema)
        .put("id", cycle * size + index)
      compareData(expectedData, record.value().asInstanceOf[Struct], expectedSchema)
      index = index + 1
    })

  }

  protected def singleTableConfigInIncrementalMode(tableName: String, columnName: String):
  java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "incrementing")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", tableName)
    props.put(s"$TOPIC.partition.count", "5")
    props.put(s"$TOPIC.poll.interval.ms", "60000")
    props.put(s"$TOPIC.incrementing.column.name", columnName)

    props
  }

  protected def singleTableConfigInIncrementalQueryMode():
  java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "incrementing")
    props.put("queryMode", "query")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.query", s"select * from $SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD")
    props.put(s"$TOPIC.partition.count", "5")
    props.put(s"$TOPIC.poll.interval.ms", "60000")
    props.put(s"$TOPIC.incrementing.column.name", "id")

    props
  }

  protected def singleTableMaxRowsConfig(maxRows: String): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "bulk")
    props.put("batch.max.rows", maxRows)
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD)
    props.put(s"$TOPIC.partition.count", "1")
    props.put(s"$TOPIC.poll.interval.ms", "60000")

    props
  }

  protected def singleTableMaxRowsConfigInIncrementalMode(maxRows: String): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "incrementing")
    props.put("batch.max.rows", maxRows)
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD)
    props.put(s"$TOPIC.partition.count", "1")
    props.put(s"$TOPIC.poll.interval.ms", "60000")
    props.put(s"$TOPIC.incrementing.column.name", "id")

    props
  }

}
