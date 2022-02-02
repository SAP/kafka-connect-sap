package com.sap.kafka.connect.source

import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.source.hana.HANASourceConnector
import org.apache.kafka.connect.data.Schema.Type
import org.apache.kafka.connect.data.{Decimal, Field, Schema, Struct}
import org.apache.kafka.connect.source.SourceRecord

import java.math.RoundingMode
import scala.collection.JavaConverters._

class HANASourceTaskConversionTest extends HANASourceTaskTestBase {

  override def beforeAll(): Unit = {
    super.beforeAll()
    connector = new HANASourceConnector
    connector.start(singleTableConfig())
    task.start(connector.taskConfigs(1).get(0))
  }

  override def afterAll(): Unit = {
    task.stop()
    connector.stop()
    super.afterAll()
  }

  test("boolean type") {
    typeConversion(Schema.BOOLEAN_SCHEMA, true, java.lang.Boolean.FALSE,
      Schema.BOOLEAN_SCHEMA, java.lang.Boolean.FALSE)
  }

  test("int type") {
    typeConversion(Schema.INT32_SCHEMA, true, new java.lang.Integer(1),
      Schema.INT32_SCHEMA, new Integer(1))
  }

  test("long type") {
    typeConversion(Schema.INT64_SCHEMA, true, new java.lang.Long(1),
      Schema.INT64_SCHEMA, new java.lang.Long(1))
  }

  test("double type") {
    typeConversion(Schema.FLOAT64_SCHEMA, true, new java.lang.Double(1.0),
      Schema.FLOAT64_SCHEMA, new java.lang.Double(1.0))
  }

  test("string type") {
    typeConversion(Schema.STRING_SCHEMA, true, "'a'",
      Schema.STRING_SCHEMA, "a")
  }

  test("decimal type") {
    val schema = Decimal.builder(2).parameter("precision", Integer.toString(10)).build()
    typeConversion(schema, true, "3.12",
      schema, new java.math.BigDecimal(3.12).setScale(2, RoundingMode.HALF_UP))
  }

  private def typeConversion(sqlType: Schema, nullable: Boolean,
                             sqlValue: Object, convertedSchema: Schema,
                             convertedValue: Object): Unit = {
    val fields = Seq(new Field("id", 1, sqlType))
    jdbcClient.createTable(Some("TEST"), "EMPLOYEES_SOURCE", MetaSchema(null, fields),
      3000)
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into \"TEST\".\"EMPLOYEES_SOURCE\" values(" + sqlValue.toString + ")")
      var records = task.poll()
      if (records == null) {
        records = task.poll()
      }
      validateRecords(records.asScala.toList, convertedSchema, convertedValue)
      stmt.execute("drop table \"TEST\".\"EMPLOYEES_SOURCE\"")
    } finally {
      connection.close()
    }
  }

  private def validateRecords(records: List[SourceRecord], expectedFieldSchema: Schema,
                              expectedValue: Object): Unit = {
    assert(records.size === 1)
    val objValue = records.head.value()
    assert(objValue.isInstanceOf[Struct])
    val value = objValue.asInstanceOf[Struct]

    val schema = value.schema()
    assert(Type.STRUCT === schema.`type`())
    val fields = schema.fields()

    assert(fields.size() === 1)

    val fieldSchema = fields.get(0).schema()
    assert(expectedFieldSchema === fieldSchema)

    assert(expectedValue === value.get(fields.get(0)))
  }
}