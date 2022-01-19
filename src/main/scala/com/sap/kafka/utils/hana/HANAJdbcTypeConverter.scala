package com.sap.kafka.utils.hana

import com.sap.kafka.client.metaAttr
import com.sap.kafka.utils.GenericJdbcTypeConverter
import org.apache.kafka.connect.data.Schema.Type
import org.apache.kafka.connect.data._

object HANAJdbcTypeConverter extends GenericJdbcTypeConverter {

  /**
   * Converts a Kafka SinkRow Schema  to the most compatible HANA SQL datatype.
   *
   * @param fieldSchema The Schema for field.
   * @return The converted HANA datatype as [[java.sql.Types]]
   */
  def convertToHANAType(fieldSchema: Schema): Int = super.convertToDBType(fieldSchema)

  /**
    * Convert HANA Table schema to Kafka Schema
    * @param tableName HANA table for which metadata is converted
    * @param datatypes sequence containing metadata for table
    * @param options properties to influence the conversion
    * @return kafka schema
    */
  def convertHANAMetadataToSchema(tableName: String, datatypes: Seq[metaAttr], options: Map[String, String]): Schema =
    super.convertJdbcMetadataToSchema(tableName, datatypes, options)
}
