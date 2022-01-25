package com.sap.kafka.connect.source

import java.util
import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.MockJdbcClient
import com.sap.kafka.connect.config.hana.HANAParameters
import com.sap.kafka.connect.source.hana.{HANASourceConnector, HANASourceTask}
import org.apache.kafka.common.utils.Time
import org.apache.kafka.connect.source.SourceTaskContext
import org.apache.kafka.connect.storage.OffsetStorageReader
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.mockito.Mockito.mock
import org.mockito.Mockito._
import org.apache.kafka.connect.data.{Field, Schema}
import org.mockito.ArgumentMatchers.any

class HANASourceTaskTestBase extends FunSuite
                              with BeforeAndAfterAll {
  val tmpdir = System.getProperty("java.io.tmpdir")
  protected val TEST_CONNECTION_URL= s"jdbc:h2:file:$tmpdir/test;INIT=CREATE SCHEMA IF NOT EXISTS TEST;DB_CLOSE_DELAY=-1"

  protected val SINGLE_TABLE_NAME_FOR_BULK_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE\""
  protected val SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_QUERY\""
  protected val SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_MAXROWS\""

  protected val SINGLE_TABLE_NAME_FOR_INCR_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_FOR_INCR_LOAD\""
  protected val SINGLE_TABLE_NAME_FOR_INCR2_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_FOR_INCR2_LOAD\""
  protected val SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_FOR_INCR_QUERY_LOAD\""
  protected val SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_MAXROWS_FOR_INCR_LOAD\""

  protected val FIRST_TABLE_NAME_FOR_MULTI_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_FOR_MULTI_LOAD\""
  protected val SECOND_TABLE_NAME_FOR_MULTI_LOAD = "\"TEST\".\"EMPLOYEES_SOURCE_SECOND_FOR_MULTI_LOAD\""

  private val PARTION_SUFFIX = "_0"
  protected val SINGLE_TABLE_PARTITION_FOR_BULK_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_BULK_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_BULK_LOAD + PARTION_SUFFIX)
  protected val SINGLE_TABLE_PARTITION_FOR_BULK_QUERY_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_BULK_QUERY_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_PARTITION_FOR_BULK_QUERY_LOAD + PARTION_SUFFIX)
  protected val SINGLE_TABLE_PARTITION_FOR_BULK_MAXROWS_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_BULK_MAXROWS_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_BULK_MAXROWS_LOAD + PARTION_SUFFIX)

  protected val SINGLE_TABLE_PARTITION_FOR_INCR_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_INCR_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_INCR_LOAD + PARTION_SUFFIX)
  protected val SINGLE_TABLE_PARTITION_FOR_INCR2_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_INCR2_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_INCR2_LOAD + PARTION_SUFFIX)
  protected val SINGLE_TABLE_PARTITION_FOR_INCR_QUERY_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_INCR_QUERY_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_INCR_QUERY_LOAD + PARTION_SUFFIX)
  protected val SINGLE_TABLE_PARTITION_FOR_INCR_MAXROWS_LOAD = new util.HashMap[String, String]()
  SINGLE_TABLE_PARTITION_FOR_INCR_MAXROWS_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SINGLE_TABLE_NAME_FOR_INCR_MAXROWS_LOAD + PARTION_SUFFIX)

  protected val FIRST_TABLE_PARTITION_FOR_MULTI_LOAD = new util.HashMap[String, String]()
  FIRST_TABLE_PARTITION_FOR_MULTI_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, FIRST_TABLE_NAME_FOR_MULTI_LOAD + PARTION_SUFFIX)
  protected val SECOND_TABLE_PARTITION_FOR_MULTI_LOAD = new util.HashMap[String, String]()
  SECOND_TABLE_PARTITION_FOR_MULTI_LOAD.put(SourceConnectorConstants.TABLE_NAME_KEY, SECOND_TABLE_NAME_FOR_MULTI_LOAD + PARTION_SUFFIX)

  protected val TOPIC = "test-topic"
  protected val SECOND_TOPIC = "test-second-topic"
  protected var time: Time = _
  protected var taskContext: SourceTaskContext = _
  protected var connector: HANASourceConnector = _
  protected var task: HANASourceTask = _
  protected var jdbcClient: MockJdbcClient = _

  override def beforeAll(): Unit = {
    time = new MockTime()
    jdbcClient = new MockJdbcClient(
      HANAParameters.getConfig(singleTableConfig()))
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      connection.createStatement().execute("DROP ALL OBJECTS DELETE FILES")

      task = new HANASourceTask(time, jdbcClient)

      val offsetStorageReader = mock(classOf[OffsetStorageReader])

      val partitions = new util.HashMap[String, String]()
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_BULK_LOAD)
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_BULK_QUERY_LOAD)
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_BULK_MAXROWS_LOAD)
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_INCR_LOAD)
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_INCR2_LOAD)
      partitions.putAll(SINGLE_TABLE_PARTITION_FOR_INCR_QUERY_LOAD)
      partitions.putAll(FIRST_TABLE_PARTITION_FOR_MULTI_LOAD)
      partitions.putAll(SECOND_TABLE_PARTITION_FOR_MULTI_LOAD)

      val offsets: java.util.Map[java.util.Map[String, String],
        java.util.Map[String, Object]] = new util.HashMap[java.util.Map[String, String],
        java.util.Map[String, Object]]()
      offsets.put(partitions, null)
      when(offsetStorageReader.offsets[String](
        any(classOf[util.Collection[util.Map[String, String]]]))).thenReturn(offsets)

      taskContext = mock(classOf[SourceTaskContext])
      when(taskContext.offsetStorageReader()).thenReturn(offsetStorageReader)

      val fields = Seq(new Field("SCHEMA_NAME", 1, Schema.STRING_SCHEMA),
        new Field("TABLE_NAME", 2, Schema.STRING_SCHEMA),
        new Field("PARTITION", 3, Schema.INT32_SCHEMA))

      val stmt = connection.createStatement()
      stmt.execute("CREATE SCHEMA IF NOT EXISTS SYS")

      val objectTableFields = Seq(new Field("SCHEMA_NAME", 1, Schema.STRING_SCHEMA),
        new Field("OBJECT_NAME", 2, Schema.STRING_SCHEMA))

      jdbcClient.createTable(Some("SYS"), "OBJECTS", MetaSchema(null, objectTableFields), 3000)

      jdbcClient.createTable(Some("SYS"), "M_CS_PARTITIONS", MetaSchema(null, fields), 3000)
      stmt.execute("insert into \"SYS\".\"M_CS_PARTITIONS\" values('TEST', 'EMPLOYEES_SOURCE', 0)")
    } finally {
      connection.close()
    }
  }

  override def afterAll(): Unit = {
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("drop table \"SYS\".\"M_CS_PARTITIONS\"")

      stmt.execute("DROP ALL OBJECTS DELETE FILES")
    } finally {
      connection.close()
    }
  }

  protected def singleTableConfig(): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "bulk")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", SINGLE_TABLE_NAME_FOR_BULK_LOAD)
    props.put(s"$TOPIC.partition.count", "1")
    props.put(s"$TOPIC.poll.interval.ms", "60000")

    props
  }

  protected def multiTableConfig(): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "bulk")
    props.put("topics", s"$TOPIC,$SECOND_TOPIC")

    props.put(s"$TOPIC.table.name", FIRST_TABLE_NAME_FOR_MULTI_LOAD)
    props.put(s"$TOPIC.partition.count", "1")
    props.put(s"$TOPIC.poll.interval.ms", "60000")

    props.put(s"$SECOND_TOPIC.table.name", SECOND_TABLE_NAME_FOR_MULTI_LOAD)
    props.put(s"$SECOND_TOPIC.partition.count", "1")
    props.put(s"$SECOND_TOPIC.poll.interval.ms", "60000")

    props
  }

  protected def singleTableQueryConfig(): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("mode", "bulk")
    props.put("queryMode", "query")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.query", s"select * from $SINGLE_TABLE_NAME_FOR_BULK_QUERY_LOAD")
    props.put(s"$TOPIC.partition.count", "1")
    props.put(s"$TOPIC.poll.interval.ms", "60000")

    props
  }

  protected def expectInitialize(partitions: util.Collection[java.util.Map[String, Object]],
                                 offsets: java.util.Map[java.util.Map[String, Object],
                                   java.util.Map[String, Object]]): Unit = {
    val reader = mock(classOf[OffsetStorageReader])
    when(taskContext.offsetStorageReader()).thenReturn(reader)
    when(reader.offsets(partitions)).thenReturn(offsets)
  }

  protected def expectInitializeNoOffsets(partitions: util.Collection[util.Map[String, Object]])
  : Unit = {
    val offsets = new util.HashMap[java.util.Map[String, Object],
      java.util.Map[String, Object]]()

    val iterator = partitions.iterator()
    while (iterator.hasNext) {
      val partition = iterator.next()
      offsets.put(partition, null)
    }

    expectInitialize(partitions, offsets)
  }

  protected def initializeTask(): Unit = {
    task.initialize(taskContext)
  }
}
