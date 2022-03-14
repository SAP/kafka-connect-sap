package com.sap.kafka.connect.transforms

import org.apache.kafka.common.cache.{Cache, LRUCache, SynchronizedCache}
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.ConnectRecord
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.transforms.Transformation
import org.apache.kafka.connect.transforms.util.Requirements.{requireMap, requireStruct}
import org.apache.kafka.connect.transforms.util.{SchemaUtil, SimpleConfig}
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import scala.util.matching.Regex
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuilder

abstract class EscapeFieldNameCharacters[R <: ConnectRecord[R]] extends Transformation[R] {
  private val log = LoggerFactory.getLogger(this.getClass)

  private var validCharRE: Regex = _
  private var validFirstCharRE: Regex = _
  private var escChar: Char = _

  private var schemaUpdateCache: Cache[Schema, Schema] = _

  override def configure(props: util.Map[String, _]): Unit = {
    val config = new SimpleConfig(EscapeFieldNameCharacters.CONFIG_DEF, props)
    val validCharPattern = config.getString(EscapeFieldNameCharacters.CONFIG_VALID_CHARS_DEFAULT)
    if (validCharPattern != null && validCharPattern.length > 0) {
      validCharRE = validCharPattern.r
    }
    val validFirstCharPattern = config.getString(EscapeFieldNameCharacters.CONFIG_VALID_CHARS_FIRST)
    if (validFirstCharPattern != null && validFirstCharPattern.length > 0) {
      validFirstCharRE = validFirstCharPattern.r
    } else {
      validFirstCharRE = validCharRE
    }
    escChar = config.getString(EscapeFieldNameCharacters.CONFIG_ESCAPE_CHAR)(0)

    schemaUpdateCache = new SynchronizedCache[Schema, Schema](new LRUCache[Schema, Schema](16))
  }

  override def apply(record: R): R = {
    if (operatingValue(record) == null) {
      record
    } else if (operatingSchema(record) == null) {
      applySchemaless(record)
    } else {
      applyWithSchema(record)
    }
  }

  private def applySchemaless(record: R): R = {
    val value = requireMap(operatingValue(record), EscapeFieldNameCharacters.PURPOSE)
    val updatedValue = new util.HashMap[String, Object](value.size)
    for (e <- value.entrySet.asScala) {
      updatedValue.put(if (validCharRE == null) decode(e.getKey) else encode(e.getKey), e.getValue)
    }
    newRecord(record, null, updatedValue)
  }

  private def applyWithSchema(record: R): R = {
    val value = requireStruct(operatingValue(record), EscapeFieldNameCharacters.PURPOSE)
    var updatedSchema = schemaUpdateCache.get(value.schema)
    if (updatedSchema == null) {
      updatedSchema = makeUpdatedSchema(value.schema)
      schemaUpdateCache.put(value.schema, updatedSchema)
    }
    var updatedValue = new Struct(updatedSchema)
    var origFieldIt = value.schema.fields().iterator
    for (field <- updatedSchema.fields.asScala) {
      updatedValue.put(field.name, value.get(origFieldIt.next))
    }
    newRecord(record, updatedSchema, updatedValue)
  }

  private def makeUpdatedSchema(schema: Schema): Schema = {
    val builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct())
    for (field <- schema.fields.asScala) {
      builder.field(if (validCharRE == null) decode(field.name) else encode(field.name) , field.schema)
    }
    builder.build
  }

  private def encode(value: String): String = {
    val buf = new StringBuilder
    var escaped = false
    for (i <- 0 to value.length - 1) {
      val re = i match {
        case 0 => validFirstCharRE
        case _ => validCharRE
      }
      val c = value.substring(i, i+1)
      if (value.charAt(i) != escChar && re.findFirstIn(c) != None) {
        buf.append(c)
      } else {
        for (b <- c.getBytes(UTF_8)) {
          buf.append(f"$escChar%c$b%x")
        }
        escaped = true
      }
    }
    escaped match {
      case true => buf.toString
      case _ => value
    }
  }

  private def decode(value: String): String = {
    val buf = new StringBuilder
    val bdec = new ArrayBuilder.ofByte
    var bdeclen = 0
    if (value.contains(escChar)) {
      var i = 0
      while (i < value.length) {
        val c = value.charAt(i)
        if (c == escChar) {
          bdec += Integer.parseInt(value.substring(i + 1, i + 3), 16).toByte
          bdeclen += 1
          i += 2
        } else {
          if (bdeclen > 0) {
            buf.append(new String(bdec.result, StandardCharsets.UTF_8))
            bdec.clear
            bdeclen = 0
          }
          buf.append(c)
        }
        i += 1
      }
      buf.toString
    } else {
      value
    }
  }

  override def close(): Unit = {
    validCharRE = null
    validFirstCharRE = null
    schemaUpdateCache = null
  }

  override def config() = EscapeFieldNameCharacters.CONFIG_DEF

  protected def operatingSchema(record: R): Schema

  protected def operatingValue(record: R): Any

  protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R
}

object EscapeFieldNameCharacters {
  private val CONFIG_VALID_CHARS_DEFAULT = "valid.chars.default"
  private val CONFIG_VALID_CHARS_FIRST = "valid.chars.first"
  private val CONFIG_ESCAPE_CHAR = "escape.char"

  val OVERVIEW_DOC = s"Escaping specific characters from the field names."
  val CONFIG_DEF = new ConfigDef()
    .define(CONFIG_VALID_CHARS_DEFAULT, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM,
      "Field name for valid characters pattern")
    .define(CONFIG_VALID_CHARS_FIRST, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM,
      "Field name for valid first characters pattern")
    .define(CONFIG_ESCAPE_CHAR, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM,
      "Field name for escape character")
  private val PURPOSE = "field replacement"

  class Key[R <: ConnectRecord[R]] extends EscapeFieldNameCharacters[R] {
    override protected def operatingSchema(record: R): Schema = {
      record.keySchema
    }

    override protected def operatingValue(record: R): Any = {
      record.key
    }

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R = {
      record.newRecord(record.topic, record.kafkaPartition, updatedSchema, updatedValue, record.valueSchema, record.value, record.timestamp)
    }
  }

  class Value[R <: ConnectRecord[R]] extends EscapeFieldNameCharacters[R] {
    override protected def operatingSchema(record: R): Schema = {
      record.valueSchema
    }

    override protected def operatingValue(record: R): Any = {
      record.value
    }

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R = {
      record.newRecord(record.topic, record.kafkaPartition, record.keySchema, record.key, updatedSchema, updatedValue, record.timestamp)
    }
  }
}