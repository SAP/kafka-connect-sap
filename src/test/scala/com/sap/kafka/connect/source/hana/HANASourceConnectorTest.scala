package com.sap.kafka.connect.source.hana

import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.MockJdbcClient
import com.sap.kafka.connect.config.hana.HANAParameters
import org.apache.kafka.connect.data.{Field, Schema}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.util

class HANASourceConnectorTest extends AnyFunSuite with BeforeAndAfterAll {
  val tmpdir = System.getProperty("java.io.tmpdir")
  protected val TEST_CONNECTION_URL= s"jdbc:h2:file:$tmpdir/test2;INIT=CREATE SCHEMA IF NOT EXISTS TEST;DB_CLOSE_DELAY=-1"
  private val TOPIC1 = "test-topic1"
  private val TOPIC2 = "test-topic2"
  private val TABLE_NAME_ONE_SOURCE = "\"TEST\".\"ONE_SOURCE\""
  private val TABLE_CONFIG_SINGLE = tableConfigSingle
  private val TABLE_NAME_TWO_SOURCE = "\"TEST\".\"TWO_SOURCE\""
  private val TABLE_CONFIG_MULTIPLE = tableConfigMultiple
  protected var jdbcClient: MockJdbcClient = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    jdbcClient = new MockJdbcClient(HANAParameters.getConfig(TABLE_CONFIG_SINGLE))
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement

      stmt.execute("DROP ALL OBJECTS DELETE FILES")
      stmt.execute("CREATE SCHEMA IF NOT EXISTS SYS")

      val fields = Seq(new Field("SCHEMA_NAME", 1, Schema.STRING_SCHEMA),
        new Field("TABLE_NAME", 2, Schema.STRING_SCHEMA),
        new Field("PARTITION", 3, Schema.INT32_SCHEMA))

      jdbcClient.createTable(Some("SYS"), "M_CS_PARTITIONS", MetaSchema(null, fields), 3000)
      stmt.execute("insert into \"SYS\".\"M_CS_PARTITIONS\" values('TEST', 'ONE_SOURCE', 0)")
      stmt.execute("insert into \"SYS\".\"M_CS_PARTITIONS\" values('TEST', 'TWO_SOURCE', 1)")
      stmt.execute("insert into \"SYS\".\"M_CS_PARTITIONS\" values('TEST', 'TWO_SOURCE', 2)")
    } finally {
      connection.close()
    }
  }

  override def afterAll(): Unit = {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement
      stmt.execute("drop table \"SYS\".\"M_CS_PARTITIONS\"")

      stmt.execute("DROP ALL OBJECTS DELETE FILES")
    } finally {
      connection.close()
    }
    super.afterAll()
  }

  test("one task for non-partitioned single table") {
    val connector = new HANASourceConnector
    try {
      connector.start(TABLE_CONFIG_SINGLE)
      val taskConfigs = connector.taskConfigs(1)
      assert(taskConfigs.size() === 1)
      verifySourceTasksConfigs(taskConfigs,
        List(
          List(Tuple3("\"TEST\".\"ONE_SOURCE\"", 0, "test-topic1"))))
    } finally {
      connector.stop()
    }
  }

  test("too many tasks for non-partitioned single table") {
    val connector = new HANASourceConnector
    try {
      connector.start(TABLE_CONFIG_SINGLE)
      val taskConfigs = connector.taskConfigs(3)
      assert(taskConfigs.size === 1)
      verifySourceTasksConfigs(taskConfigs,
        List(
          List(Tuple3("\"TEST\".\"ONE_SOURCE\"", 0, "test-topic1"))))
    } finally {
      connector.stop()
    }
  }

  test("multiple tasks for partitioned multiple tables") {
    val connector = new HANASourceConnector
    try {
      connector.start(TABLE_CONFIG_MULTIPLE)
      val taskConfigs = connector.taskConfigs(3)
      assert(taskConfigs.size === 3)
      verifySourceTasksConfigs(taskConfigs,
        List(
          List(Tuple3("\"TEST\".\"ONE_SOURCE\"", 0, "test-topic1")),
          List(Tuple3("\"TEST\".\"TWO_SOURCE\"", 1, "test-topic2")),
          List(Tuple3("\"TEST\".\"TWO_SOURCE\"", 2, "test-topic2"))))
    } finally {
      connector.stop()
    }
  }

  test("too many tasks for partitioned multiple tables") {
    val connector = new HANASourceConnector
    try {
      connector.start(TABLE_CONFIG_MULTIPLE)
      val taskConfigs = connector.taskConfigs(5)
      assert(taskConfigs.size === 3)
      verifySourceTasksConfigs(taskConfigs,
        List(
          List(Tuple3("\"TEST\".\"ONE_SOURCE\"", 0, "test-topic1")),
          List(Tuple3("\"TEST\".\"TWO_SOURCE\"", 1, "test-topic2")),
          List(Tuple3("\"TEST\".\"TWO_SOURCE\"", 2, "test-topic2"))))
    } finally {
      connector.stop()
    }
  }

  test("less tasks for partitioned multiple table") {
    val connector = new HANASourceConnector
    try {
      connector.start(TABLE_CONFIG_MULTIPLE)
      val taskConfigs = connector.taskConfigs(2)
      assert(taskConfigs.size === 2)
      verifySourceTasksConfigs(taskConfigs,
        List(
          List(Tuple3("\"TEST\".\"ONE_SOURCE\"", 0, "test-topic1"), Tuple3("\"TEST\".\"TWO_SOURCE\"", 1, "test-topic2")),
          List(Tuple3("\"TEST\".\"TWO_SOURCE\"", 2, "test-topic2"))))
    } finally {
      connector.stop()
    }
  }

  def verifySourceTasksConfigs(taskConfigs: util.List[util.Map[String, String]], expected: List[List[Tuple3[String, Int, String]]]) : Unit = {
    val tcit = taskConfigs.iterator
    for (tq <- expected) {
      if (tcit.hasNext) {
        val task = new HANASourceTask
        try {
          task.start(tcit.next)
          assert(tq === task.getTableOrQueryInfos())
        } finally {
          task.stop()
        }
      } else {
        fail("Unexpected number of tasks")
      }
    }
  }

  def tableConfigBase(): util.Map[String, String] = {
    val props = new util.HashMap[String, String]()
    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "bulk")
    props
  }

  def tableConfigSingle(): util.Map[String, String] = {
    val props = tableConfigBase
    props.put("topics", TOPIC1)
    props.put(s"$TOPIC1.table.name", TABLE_NAME_ONE_SOURCE)
    props.put(s"$TOPIC1.partition.count", "1")
    props.put(s"$TOPIC1.poll.interval.ms", "60000")
    props
  }

  def tableConfigMultiple(): util.Map[String, String] = {
    val props = tableConfigBase
    props.put("topics", s"$TOPIC1,$TOPIC2")
    props.put(s"$TOPIC1.table.name", TABLE_NAME_ONE_SOURCE)
    props.put(s"$TOPIC1.partition.count", "1")
    props.put(s"$TOPIC1.poll.interval.ms", "60000")
    props.put(s"$TOPIC2.table.name", TABLE_NAME_TWO_SOURCE)
    props.put(s"$TOPIC2.partition.count", "1")
    props.put(s"$TOPIC2.poll.interval.ms", "60000")
    props
  }
}
