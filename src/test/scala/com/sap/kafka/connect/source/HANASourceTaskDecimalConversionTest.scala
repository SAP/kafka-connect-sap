package com.sap.kafka.connect.source

import com.sap.kafka.client.MetaSchema
import com.sap.kafka.connect.config.BaseConfigConstants
import com.sap.kafka.connect.source.hana.HANASourceConnector
import org.apache.kafka.connect.data.Schema.Type
import org.apache.kafka.connect.data.{Decimal, Field, Schema, Struct}
import org.apache.kafka.connect.source.SourceRecord

import java.math.RoundingMode
import scala.collection.JavaConverters._

class HANASourceTaskDecimalConversionTest extends HANASourceTaskTestBase {
  override def beforeAll(): Unit = {
    super.beforeAll()
    connector = new HANASourceConnector
    connector.start(singleTableNumericMappingConfig())
    task.start(connector.taskConfigs(1).get(0))
  }

  override def afterAll(): Unit = {
    task.stop()
    connector.stop()
    super.afterAll()
  }

  test("decimal type with numeric.mapping") {
    val fields = Seq(
      new Field("dec1_p10s2", 1, Decimal.builder(2).build()),
      new Field("dec2_p10s2", 2, Decimal.builder(2).build()),
      new Field("dec3_p10s0", 3, Decimal.builder(0).build()))

    jdbcClient.createTable(Some("TEST"),"EMPLOYEES_SOURCE", MetaSchema(null, fields),
      3000)
    val connection = jdbcClient.getConnection
    try {
      connection.setAutoCommit(true)
      val stmt = connection.createStatement()
      stmt.execute("insert into \"TEST\".\"EMPLOYEES_SOURCE\" values(3.14, 314, 314)")

      var records = task.poll()
      if (records == null) {
        records = task.poll()
      }
      assert(records.size === 1)
      val objValue = records.get(0).value
      assert(objValue.isInstanceOf[Struct])
      val value = objValue.asInstanceOf[Struct]

      val schema = value.schema()
      val fields = schema.fields()

      assert(fields.size() === 3)
      validateField(0, fields, value, Schema.FLOAT64_SCHEMA, java.lang.Double.valueOf(3.14))
      validateField(1, fields, value, Schema.FLOAT64_SCHEMA, java.lang.Double.valueOf(314))
      validateField(2, fields, value, Schema.INT64_SCHEMA, java.lang.Long.valueOf(314))

      stmt.execute("drop table \"TEST\".\"EMPLOYEES_SOURCE\"")
    } finally {
      connection.close()
    }
  }

  private def validateField(index: Int, fields: java.util.List[Field], value: Struct, expectedSchema: Schema, expectedValue: Object): Unit = {
    val field = fields.get(index)
    assert(expectedSchema === field.schema)
    assert(expectedValue === value.get(field))
  }

  private def singleTableNumericMappingConfig(): java.util.Map[String, String] = {
    val config = singleTableConfig()
    config.put("numeric.mapping", BaseConfigConstants.NUMERIC_MAPPING_BEST_FIT)
    config
  }
}