package com.sap.kafka.client

/**
  * A row from of the result of a query to HANA's TABLE_COLUMNS system table.
  *
  * @param schema The schema the table lies in.
  * @param tableName The table name.
  * @param name The column name.
  * @param dataType The column data type string.
  * @param length The length of the data type.
  * @param scale The scale of the data type.
  * @param index The index of the column.
  * @param nullable `true` if the column is nullable, `false` otherwise.
  */
case class ColumnRow(
    schema: String,
    tableName: String,
    name: String,
    dataType: String,
    length: Int,
    scale: Int,
    index: Int,
    nullable: Boolean)
