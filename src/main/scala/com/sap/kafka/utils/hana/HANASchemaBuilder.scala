package com.sap.kafka.utils.hana

import com.sap.kafka.client.MetaSchema
import com.sap.kafka.utils.GenericSchemaBuilder


object HANASchemaBuilder extends GenericSchemaBuilder {
  /**
   * Converts a AVRO schema to the HANA Schema.
   *
   * @param schema The schema to convert
   * @return The HANA schema as [[String]]
   */
  def avroToHANASchema(schema: MetaSchema): String =
    super.avroToJdbcSchema(schema)
}
