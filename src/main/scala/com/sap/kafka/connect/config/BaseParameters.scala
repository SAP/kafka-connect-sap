package com.sap.kafka.connect.config

import com.sap.kafka.client.hana.HANAConfigMissingException

object BaseConfigConstants {
  val RECORD_KEY = "record_key"
  val RECORD_VALUE = "record_value"

  val TABLE_NAME_FORMAT = "\"(.+)\"\\.\"(.+)\"".r
  val COLLECTION_NAME_FORMAT = "\"(.+)\"".r

  val QUERY_MODE_TABLE = "table"
  val QUERY_MODE_SQL = "query"

  val INSERT_MODE_INSERT = "insert"
  val INSERT_MODE_UPSERT = "upsert"

  val MODE_BULK = "bulk"
  val MODE_INCREMENTING = "incrementing"

  val COLUMN_TABLE_TYPE = "column"
  val ROW_TABLE_TYPE = "row"
  val COLLECTION_TABLE_TYPE = "collection"

  val NO_PARTITION = "none"
  val HASH_PARTITION = "hash"
  val ROUND_ROBIN_PARTITION = "round_robin"

  val IN_MEMORY_ENGINE = "in-memory"
  val DISK_ENGINE = "disk"

  val NUMERIC_MAPPING_NONE = "none"
  val NUMERIC_MAPPING_BEST_FIT = "best_fit"
  val NUMERIC_MAPPING_BEST_FIT_EAGER_DOUBLE = "best_fit_eager_double"
}

trait BaseParameters {
  def getConfig(props: java.util.Map[String, String]): BaseConfig = {


    if (props.get("topics") == null) {
      throw new HANAConfigMissingException("Mandatory parameter missing: " +
        "A comma-separated list of topics is required to run the HANA-Kafka connectors")
    }

    null
  }
}
