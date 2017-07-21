package com.sap.kafka.utils

import java.sql.{PreparedStatement, ResultSetMetaData}
import java.text.SimpleDateFormat

import com.sap.kafka.client.metaAttr
import org.apache.kafka.connect.data._
import org.slf4j.LoggerFactory

trait GenericJdbcTypeConverter {
  private val log = LoggerFactory.getLogger(getClass)

  /**
    * Converts a Kafka SinkRow Schema  to the most compatible Jdbc SQL datatype.
    *
    * @param fieldSchema The Schema for field.
    * @return The converted Jdbc datatype as [[java.sql.Types]]
    */
  def convertToDBType(fieldSchema: Schema): Int = {
    val fieldType = fieldSchema.`type`()
    val fieldLogicalType = fieldSchema.name()

    if (fieldLogicalType != null) {
      if (convertToDBType(fieldLogicalType) != -1) {
        return convertToDBType(fieldLogicalType)
      }
    }

    fieldType match {
      case Schema.Type.INT8 => java.sql.Types.INTEGER
      case Schema.Type.INT16 => java.sql.Types.INTEGER
      case Schema.Type.INT32 => java.sql.Types.INTEGER
      case Schema.Type.INT64 => java.sql.Types.BIGINT
      case Schema.Type.FLOAT64 => java.sql.Types.DOUBLE
      case Schema.Type.FLOAT32 => java.sql.Types.REAL
      case Schema.Type.BOOLEAN => java.sql.Types.BIT
      case Schema.Type.STRING => java.sql.Types.VARCHAR
      case Schema.Type.BYTES => java.sql.Types.BLOB
      case _ => sys.error(s"Unsupported Avro type: ${fieldSchema.`type`()}")
    }
  }

  private def convertToDBType(fieldLogicalType: String): Int = {
    fieldLogicalType match {
      case Date.LOGICAL_NAME => java.sql.Types.DATE
      case Decimal.LOGICAL_NAME => java.sql.Types.DECIMAL
      case Time.LOGICAL_NAME => java.sql.Types.TIME
      case Timestamp.LOGICAL_NAME => java.sql.Types.TIMESTAMP
      case _ => -1
    }
  }

  /**
    * Generates a sequence of setters which set values in the provided [[PreparedStatement]]
    * object using a proper setter method for each value datatype.
    *
    * @param datatypes The datatypes of the SinkRow Schema
    * @param stmt The [[PreparedStatement]] object on which the setters are supposed to be called
    * @return A sequence of setter functions which argument is the value to be set
    *         of the type [[Any]]
    */
  def getSinkRowDatatypesSetters(datatypes: Seq[metaAttr], stmt: PreparedStatement):
  Seq[(Any) => Unit] = datatypes.zipWithIndex.map({case (t, i) => t.dataType match {
    case java.sql.Types.BOOLEAN => (value: Any) => stmt.setBoolean(i + 1, value.asInstanceOf[Boolean])
    case java.sql.Types.BIT => (value: Any) => stmt.setBoolean(i + 1, value.asInstanceOf[Boolean])
    case java.sql.Types.TINYINT => (value: Any) => stmt.setShort(i + 1, value.asInstanceOf[java.lang.Short])
    case java.sql.Types.SMALLINT => (value: Any) => stmt.setShort(i + 1, value.asInstanceOf[java.lang.Short])
    case java.sql.Types.INTEGER => (value: Any) => stmt.setInt(i + 1, value.asInstanceOf[Int])
    case java.sql.Types.BIGINT => (value: Any) => stmt.setLong(i + 1, value.asInstanceOf[java.lang.Long])
    case java.sql.Types.REAL => (value: Any) => stmt.setFloat(i + 1, value.asInstanceOf[Float])
    case java.sql.Types.FLOAT => (value: Any) => stmt.setDouble(i + 1, value.asInstanceOf[Double])
    case java.sql.Types.DOUBLE => (value: Any) => stmt.setDouble(i + 1, value.asInstanceOf[Double])
    case java.sql.Types.NUMERIC => (value: Any) => stmt.setBigDecimal(i + 1, value.asInstanceOf[java.math.BigDecimal])
    case java.sql.Types.DECIMAL => (value: Any) => stmt.setBigDecimal(i + 1, value.asInstanceOf[java.math.BigDecimal])
    case java.sql.Types.CHAR | java.sql.Types.VARCHAR
         | java.sql.Types.LONGNVARCHAR | java.sql.Types.NCHAR
         | java.sql.Types.NVARCHAR | java.sql.Types.CLOB |
         java.sql.Types.NCLOB | java.sql.Types.DATALINK
         | java.sql.Types.SQLXML => (value: Any) => stmt.setString(i + 1, value.asInstanceOf[String])
    case java.sql.Types.BINARY | java.sql.Types.BLOB | java.sql.Types.VARBINARY |
         java.sql.Types.LONGVARBINARY => (value: Any)  =>
      stmt.setBytes(i + 1, value.asInstanceOf[Array[Byte]])
    case java.sql.Types.DATE => (value: Any) => stmt.setDate(i + 1, convertToJdbcDateTypeFromAvroDateType(value))
    case java.sql.Types.TIME => (value: Any) => stmt.setTime(i + 1, convertToJdbcTimeTypeFromAvroTimeType(value))
    case java.sql.Types.TIMESTAMP => (value: Any) => stmt.setTimestamp(i + 1, convertToJdbcTypeFromAvroTimestampType(value))
    case other =>
      (value: Any) =>
        sys.error(s"Unable to translate the non-null value for the field $i")
  }})

  private def convertToJdbcDateTypeFromAvroDateType(value: Any): java.sql.Date = {
    val avroDateParser = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")

    new java.sql.Date(avroDateParser.parse(value.toString).getTime)
  }

  private def convertToJdbcTimeTypeFromAvroTimeType(value: Any): java.sql.Time = {
    try {
      val avroTimeParser = new SimpleDateFormat("E MMM dd HH:mm:ss.SSS Z yyyy")

      new java.sql.Time(avroTimeParser.parse(value.toString).getTime)
    } catch {
      case e: Exception =>
        val avroTimeParser = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")

        new java.sql.Time(avroTimeParser.parse(value.toString).getTime)
    }
  }

  private def convertToJdbcTypeFromAvroTimestampType(value: Any): java.sql.Timestamp = {
    try {
      val avroTimestampParser = new SimpleDateFormat("E MMM dd HH:mm:ss.SSS Z yyyy")

      new java.sql.Timestamp(avroTimestampParser.parse(value.toString).getTime)
    } catch {
      case e: Exception =>
        val avroTimestampParser = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")

        new java.sql.Timestamp(avroTimestampParser.parse(value.toString).getTime)
    }
  }

  /**
    * Convert Jdbc schema to Kafka Schema
    * @param tableName table for which metadata is converted
    * @param datatypes sequence containing metadata for table
    * @return kafka schema
    */
  def convertJdbcMetadataToSchema(tableName: String, datatypes: Seq[metaAttr]): Schema = {
    val builder = org.apache.kafka.connect.data.SchemaBuilder.struct().name(tableName
      .replaceAll("[^\\w\\s]", "").toLowerCase)
    for ( i <- 1 to datatypes.size) {
      addFieldSchema(datatypes, i-1, builder)
    }
    builder.build()
  }

  private def addFieldSchema(datatypes: Seq[metaAttr], col: Int,
                             builder: org.apache.kafka.connect.data.SchemaBuilder): Unit = {
    val fieldname = datatypes(col).name
    val sqlType = datatypes(col).dataType

    var optional = false
    if (datatypes(col).isNullable == ResultSetMetaData.columnNullable ||
      datatypes(col).isNullable == ResultSetMetaData.columnNullableUnknown) {
      optional = true
    }

    sqlType match {
      case java.sql.Types.NULL =>
        log.warn("JDBC type {} not currently supported", sqlType)
      case java.sql.Types.BOOLEAN =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_BOOLEAN_SCHEMA)
        else
          builder.field(fieldname, Schema.BOOLEAN_SCHEMA)
      case java.sql.Types.BIT | java.sql.Types.TINYINT =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_INT8_SCHEMA)
        else
          builder.field(fieldname, Schema.INT8_SCHEMA)
      case java.sql.Types.SMALLINT =>
        if (optional) {
          builder.field(fieldname, Schema.OPTIONAL_INT16_SCHEMA)
        }
        else
          builder.field(fieldname, Schema.INT16_SCHEMA)
      case java.sql.Types.INTEGER =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_INT32_SCHEMA)
        else
          builder.field(fieldname, Schema.INT32_SCHEMA)
      case java.sql.Types.BIGINT =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_INT64_SCHEMA)
        else
          builder.field(fieldname, Schema.INT64_SCHEMA)
      case java.sql.Types.REAL =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_FLOAT32_SCHEMA)
        else
          builder.field(fieldname, Schema.FLOAT32_SCHEMA)
      case java.sql.Types.FLOAT | java.sql.Types.DOUBLE =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_FLOAT64_SCHEMA)
        else
          builder.field(fieldname, Schema.FLOAT64_SCHEMA)
      case java.sql.Types.NUMERIC | java.sql.Types.DECIMAL =>
        val fieldBuilder = Decimal.builder(datatypes(col).scale)
        if (optional)
          fieldBuilder.optional()
        builder.field(fieldname, fieldBuilder.build())
      case java.sql.Types.CHAR | java.sql.Types.VARCHAR | java.sql.Types.LONGNVARCHAR |
           java.sql.Types.NCHAR | java.sql.Types.NVARCHAR | java.sql.Types.LONGNVARCHAR |
           java.sql.Types.CLOB | java.sql.Types.NCLOB | java.sql.Types.DATALINK |
           java.sql.Types.SQLXML =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_STRING_SCHEMA)
        else
          builder.field(fieldname, Schema.STRING_SCHEMA)
      case java.sql.Types.BINARY | java.sql.Types.BLOB | java.sql.Types.VARBINARY |
           java.sql.Types.LONGVARBINARY =>
        if (optional)
          builder.field(fieldname, Schema.OPTIONAL_BYTES_SCHEMA)
        else
          builder.field(fieldname, Schema.BYTES_SCHEMA)
      case java.sql.Types.DATE =>
        val dateSchemaBuilder = Date.builder()
        if (optional)
          dateSchemaBuilder.optional()
        builder.field(fieldname, dateSchemaBuilder)
      case java.sql.Types.TIME =>
        val timeSchemaBuilder = Time.builder()
        if (optional)
          timeSchemaBuilder.optional()
        builder.field(fieldname, timeSchemaBuilder)
      case java.sql.Types.TIMESTAMP =>
        val tsSchemaBuilder = Timestamp.builder()
        if (optional)
          tsSchemaBuilder.optional()
        builder.field(fieldname, tsSchemaBuilder)
      case java.sql.Types.ARRAY | java.sql.Types.JAVA_OBJECT | java.sql.Types.OTHER |
           java.sql.Types.DISTINCT | java.sql.Types.STRUCT | java.sql.Types.REF |
           java.sql.Types.ROWID =>
        log.warn("JDBC type {} not currently supported", sqlType)
      case _ => log.warn("JDBC type {} not currently supported", sqlType)
    }
  }
}