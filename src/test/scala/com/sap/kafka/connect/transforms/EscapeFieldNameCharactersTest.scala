package com.sap.kafka.connect.transforms

import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.source.SourceRecord
import org.scalatest.{BeforeAndAfterEach, FunSuite}

import java.util

class EscapeFieldNameCharactersTest extends FunSuite with BeforeAndAfterEach {
  val xform = new EscapeFieldNameCharacters.Value[SourceRecord]

  override protected def afterEach(): Unit = {
    xform.close()
  }

  test("escape chars for avro") {
    val props = new util.HashMap[String, Object]()
    props.put("valid.chars.default", "[A-Za-z0-9_]")
    props.put("valid.chars.first", "[A-Za-z_]")
    props.put("escape.char", "_")

    xform.configure(props)
    val valueSchema = SchemaBuilder.struct()
      .name("schema with unwanted field names")
      .field("id", Schema.INT32_SCHEMA)
      .field("x/field", Schema.STRING_SCHEMA)
      .field("123", Schema.STRING_SCHEMA).build()
    val value = new Struct(valueSchema)
      .put("id", 23)
      .put("x/field", "xy")
      .put("123", "0307")
    val rec = new SourceRecord(null, null, "test", 0, valueSchema, value)
    val recout = xform.apply(rec)

    val recoutValue = recout.value.asInstanceOf[Struct]
    assert(recoutValue.getInt32("id") === 23)
    assert(recoutValue.getString("x_2ffield") === "xy")
    assert(recoutValue.getString("_3123") === "0307")

    val recoutSchema = recout.valueSchema
    assert(recoutSchema.field("id").index === 0)
    assert(recoutSchema.field("x_2ffield").index === 1)
    assert(recoutSchema.field("_3123").index === 2)
  }

  test("unescape chars for avro") {
    val props = new util.HashMap[String, Object]()
    props.put("escape.char", "_")

    xform.configure(props)
    val valueSchema = SchemaBuilder.struct()
      .name("schema with unwanted field names")
      .field("id", Schema.INT32_SCHEMA)
      .field("x_2ffield", Schema.STRING_SCHEMA)
      .field("_3123", Schema.STRING_SCHEMA).build()
    val value = new Struct(valueSchema)
      .put("id", 23)
      .put("x_2ffield", "xy")
      .put("_3123", "0307")
    val rec = new SourceRecord(null, null, "test", 0, valueSchema, value)
    val recout = xform.apply(rec)

    val recoutValue = recout.value.asInstanceOf[Struct]
    assert(recoutValue.getInt32("id") === 23)
    assert(recoutValue.getString("x/field") === "xy")
    assert(recoutValue.getString("123") === "0307")

    val recoutSchema = recout.valueSchema
    assert(recoutSchema.field("id").index === 0)
    assert(recoutSchema.field("x/field").index === 1)
    assert(recoutSchema.field("123").index === 2)
  }
}
