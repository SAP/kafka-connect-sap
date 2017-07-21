package com.sap.kafka.utils

import com.sap.kafka.client.MetaSchema
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.data.Schema.Type

import scala.collection.JavaConversions._

trait GenericSchemaBuilder {

  /**
    * Converts a Kafka schema to the Jdbc Schema.
    *
    * @param schema The schema to convert
    * @return The Jdbc schema as [[String]]
    */
  def avroToJdbcSchema(schema: MetaSchema): String = {
    schema.avroFields.map({ field =>
      val name = field.name

      val hanaType = if (field.schema().name() != null)
        getLogicalTypeFromFieldSchema(field.schema())
      else
        avroToJdbcType(field.schema().`type`())

      val nullModifier = if (field.schema().isOptional) "NULL" else "NOT NULL"
      s""""$name" $hanaType $nullModifier"""
    }).mkString(", ")
  }

   def avroToJdbcType(avroType: Type): String = {
    typeToSql(avroType)
  }

  protected def typeToSql(schemaType: Type): String = {
    schemaType match {
      case Schema.Type.INT8 => "TINYINT"
      case Schema.Type.INT16 => "SMALLINT"
      case Schema.Type.INT32 => "INTEGER"
      case Schema.Type.INT64 => "BIGINT"
      case Schema.Type.FLOAT32 => "FLOAT"
      case Schema.Type.FLOAT64 => "DOUBLE"
      case Schema.Type.BOOLEAN => "BOOLEAN"
      case Schema.Type.STRING => "VARCHAR(*)"
      case _ =>
        throw new IllegalArgumentException(s"Type $schemaType cannot be converted to SQL type")
    }
  }

  private def getLogicalTypeFromFieldSchema(fieldSchema: Schema): String = {
    val logicalType = fieldSchema.name()

    var parameters = Map[String, String]()

    if (fieldSchema.parameters() != null) {
      parameters = fieldSchema.parameters().toMap
    }

    logicalType match {
      case Date.LOGICAL_NAME => "DATE"
      case Decimal.LOGICAL_NAME =>
        s"""DECIMAL(10, ${parameters(Decimal.SCALE_FIELD)})"""
      case Time.LOGICAL_NAME => "TIME"
      case Timestamp.LOGICAL_NAME => "TIMESTAMP"
      case _ => throw new ConnectorException("Field Schema type is invalid")
    }
  }
}