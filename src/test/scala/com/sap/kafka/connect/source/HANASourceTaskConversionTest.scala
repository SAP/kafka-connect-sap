package com.sap.kafka.connect.source

import java.lang.{Boolean, Double, Float, Long}
import com.sap.kafka.client.MetaSchema
import org.apache.kafka.connect.data.Schema.Type
import org.apache.kafka.connect.data.{Field, Schema, Struct}
import org.apache.kafka.connect.source.SourceRecord

import scala.collection.JavaConversions._

class HANASourceTaskConversionTest extends HANASourceTaskTestBase {

  override def beforeAll(): Unit = {
    super.beforeAll()
    task.start(singleTableConfig())
  }

  override def afterAll(): Unit = {
    task.stop()
    super.afterAll()
  }

  test("boolean type") {
    typeConversion(Schema.BOOLEAN_SCHEMA, true, new Boolean(false),
      Schema.BOOLEAN_SCHEMA, new Boolean(false))
  }

  test("int type") {
    typeConversion(Schema.INT32_SCHEMA, true, new Integer(1),
      Schema.INT32_SCHEMA, new Integer(1))
  }

  test("long type") {
    typeConversion(Schema.INT64_SCHEMA, true, new Long(1),
      Schema.INT64_SCHEMA, new Long(1))
  }

  test("double type") {
    typeConversion(Schema.FLOAT64_SCHEMA, true, new Double(1.0),
      Schema.FLOAT64_SCHEMA, new Double(1.0))
  }

  test("string type") {
    typeConversion(Schema.STRING_SCHEMA, true, "'a'",
      Schema.STRING_SCHEMA, "a")
  }

  private def typeConversion(sqlType: Schema, nullable: Boolean,
                             sqlValue: Object, convertedSchema: Schema,
                             convertedValue: Object): Unit = {
    val fields = Seq(new Field("id", 1, sqlType))
    jdbcClient.createTable(Some("TEST"), "EMPLOYEES_SOURCE", MetaSchema(null, fields),
      3000)
    val connection = jdbcClient.getConnection
    val stmt = connection.createStatement()
    stmt.execute("insert into \"TEST\".\"EMPLOYEES_SOURCE\" values(" + sqlValue.toString + ")")
    val records = task.poll()
    validateRecords(records.toList, convertedSchema, convertedValue)
    stmt.execute("drop table \"TEST\".\"EMPLOYEES_SOURCE\"")
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