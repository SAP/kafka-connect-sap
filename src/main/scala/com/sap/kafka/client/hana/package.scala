package com.sap.kafka

package object hanaClient {

  // Variables from the spark context have to be prefixed by
  protected[hanaClient] val CONF_PREFIX = "spark.hana."

  protected[hanaClient] val PARAMETER_PATH = "tablepath"
  protected[hanaClient] val PARAMETER_DBSCHEMA = "dbschema"
  protected[hanaClient] val PARAMETER_HOST = "host"
  protected[hanaClient] val PARAMETER_INSTANCE_ID = "instance"
  protected[hanaClient] val PARAMETER_USER = "user"
  protected[hanaClient] val PARAMETER_PASSWORD = "passwd"
  protected[hanaClient] val PARAMETER_PARTITION_COLUMN = "partitioningColumn"
  protected[hanaClient] val PARAMETER_NUM_PARTITIONS = "numberOfPartitions"
  protected[hanaClient] val PARAMETER_MAX_PARTITION_SIZE = "maximumPartitionSize"
  protected[hanaClient] val PARAMETER_BATCHSIZE = "batchsize"
  protected[hanaClient] val DEFAULT_BATCHSIZE = 1000
  protected[hanaClient] val DEFAULT_NUM_PARTITIONS = 1
  protected[hanaClient] val PARAMETER_SCHEMA = "schema"
  protected[hanaClient] val PARAMETER_LOWER_BOUND = "lowerBound"
  protected[hanaClient] val PARAMETER_UPPER_BOUND = "upperBound"
  protected[hanaClient] val MAX_PARTITION_SIZE = 100000
  protected[hanaClient] val PARAMETER_COLUMN_TABLE = "columnStore"
  protected[hanaClient] val DEFAULT_COLUMN_TABLE = false
  protected[hanaClient] val PARAMETER_TABLE_PATTERN = "tablePattern"
  protected[hanaClient] val DEFAULT_TABLE_PATTERN = "%"
  protected[hanaClient] val PARAMETER_VIRTUAL_TABLE = "virtual"
  protected[hanaClient] val DEFAULT_VIRTUAL_TABLE = true
  protected[hanaClient] val PARAMETER_TENANT_DATABASE = "tenantdatabase"
  protected[hanaClient] val PARAMETER_PORT = "port"
  protected[hanaClient] val DEFAULT_SINGLE_CONT_HANA_PORT = "15"
  protected[hanaClient] val DEFAULT_MULTI_CONT_HANA_PORT = "13"

  protected[hanaClient] val CONSTANT_STORAGE_TYPE  = "STORAGE_TYPE"
  protected[hanaClient] val CONSTANT_COLUMN_STORE  = "COLUMN STORE"
  protected[hanaClient] val CONSTANT_ROW_STORE  = "ROW STORE"


}
