package com.sap.kafka.connect.sink

import java.sql.{Connection, DatabaseMetaData, Driver, DriverAction, DriverManager, DriverPropertyInfo, PreparedStatement, ResultSet, ResultSetMetaData, Statement, Types}
import java.util
import java.util.Collections

import com.sap.kafka.connect.MockJdbcDriver
import com.sap.kafka.connect.sink.hana.HANASinkTask
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.sink.{SinkRecord, SinkTaskContext}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class HANASinkTaskTest extends FunSuite with BeforeAndAfterAll {
  private val TEST_CONNECTION_URL= s"jdbc:sapmock://demo.hana.sap.com:50000/"
  private val TOPIC = "test-topic"
  private val SINGLE_TABLE_NAME_FOR_INSERT = "\"TEST\".\"EMPLOYEES_SINK\""

  private var namedDriver: Driver = _
  private var mockDriver: Driver = _
  private var mockTaskContext: SinkTaskContext = _
  private var mockConnection: Connection = _
  private var mockStatement: Statement = _
  private var mockPreparedStatement: PreparedStatement = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    mockDriver = mock(classOf[Driver])
    when(mockDriver.acceptsURL(TEST_CONNECTION_URL)).thenReturn(true)
    when(mockDriver.getMajorVersion()).thenReturn(1)

    namedDriver = new MockJdbcDriver(mockDriver)
    DriverManager.registerDriver(namedDriver)
  }

  override def afterAll(): Unit = {
    DriverManager.deregisterDriver(namedDriver)
    super.afterAll()
  }

  val valueSchema = SchemaBuilder.struct()
    .name("schema for single table")
    .field("id", Schema.INT32_SCHEMA)
  val value = new Struct(valueSchema).put("id", 23)

  val keySchema = SchemaBuilder.struct()
    .name("schema for single table")
    .field("id", Schema.INT32_SCHEMA)
  val key = new Struct(keySchema).put("id", 23)

  test("sink create and insert") {
    verifySinkTask(null, null, valueSchema, value)
  }

  test("sink create and insert with key-schema") {
    verifySinkTask(keySchema, key, valueSchema, value)
  }

  def verifySinkTask(keySchema: Schema, key: Any, valueSchema: Schema, value: Any): Unit = {
    mockTaskContext = mock(classOf[SinkTaskContext])
    mockConnection = mock(classOf[Connection])
    mockStatement = mock(classOf[Statement])
    mockPreparedStatement = mock(classOf[PreparedStatement])
    val mockDatabaseMetaData = mock(classOf[DatabaseMetaData])
    val mockResultSetTableExists = mock(classOf[ResultSet])
    val mockResultSetSelectMetaData = mock(classOf[ResultSet])
    val mockResultSetMetaData = mock(classOf[ResultSetMetaData])

    when(mockDriver.connect(ArgumentMatchers.anyString, ArgumentMatchers.any(classOf[java.util.Properties]))).thenReturn(mockConnection)
    when(mockConnection.getMetaData).thenReturn(mockDatabaseMetaData)
    when(mockDatabaseMetaData.getTables(null, "TEST", "EMPLOYEES_SINK", null)).thenReturn(mockResultSetTableExists)
    when(mockConnection.createStatement).thenReturn(mockStatement)

    when(mockStatement.execute(ArgumentMatchers.anyString)).thenReturn(true)
    when(mockStatement.executeQuery(ArgumentMatchers.anyString)).thenReturn(mockResultSetSelectMetaData)
    when(mockResultSetSelectMetaData.getMetaData).thenReturn(mockResultSetMetaData)
    when(mockResultSetMetaData.getColumnCount).thenReturn(1)
    when(mockResultSetMetaData.getColumnName(1)).thenReturn("id")
    when(mockResultSetMetaData.getColumnType(1)).thenReturn(Types.INTEGER)
    when(mockResultSetMetaData.isNullable(1)).thenReturn(ResultSetMetaData.columnNullable)
    when(mockConnection.prepareStatement(ArgumentMatchers.anyString)).thenReturn(mockPreparedStatement)

    val task: HANASinkTask = new HANASinkTask()
    task.initialize(mockTaskContext)

    task.start(singleTableConfig())

    task.put(Collections.singleton(new SinkRecord(TOPIC, 1, keySchema, key, valueSchema, value, 0)))

    verify(mockStatement).execute("CREATE COLUMN TABLE \"TEST\".\"EMPLOYEES_SINK\" (\"id\" INTEGER NOT NULL)")
    verify(mockConnection).prepareStatement("INSERT INTO \"TEST\".\"EMPLOYEES_SINK\" (\"id\") VALUES (?)")
    verify(mockPreparedStatement).setInt(1, 23)
    verify(mockPreparedStatement).addBatch()
    verify(mockPreparedStatement).executeBatch
    verify(mockPreparedStatement).clearParameters()
    verify(mockPreparedStatement).close()
  }


  protected def singleTableConfig(): java.util.Map[String, String] = {
    val props = new util.HashMap[String, String]()

    props.put("connection.url", TEST_CONNECTION_URL)
    props.put("connection.user", "sa")
    props.put("connection.password", "sa")
    props.put("auto.create", "true")
    props.put("topics", TOPIC)
    props.put(s"$TOPIC.table.name", SINGLE_TABLE_NAME_FOR_INSERT)
    props
  }
}
