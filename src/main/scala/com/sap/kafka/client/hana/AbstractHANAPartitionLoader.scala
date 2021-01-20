package com.sap.kafka.client.hana


import java.sql.{Connection, PreparedStatement}
import java.util.function.Consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.common.base.Preconditions
import com.sap.kafka.client.{MetaSchema, metaAttr}
import com.sap.kafka.connect.config.BaseConfigConstants
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.utils.WithCloseables
import com.sap.kafka.utils.hana.HANAJdbcTypeConverter
import org.apache.kafka.connect.data.{Field, Schema, Struct}
import org.apache.kafka.connect.sink.SinkRecord
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

trait AbstractHANAPartitionLoader {

  private val log: Logger = LoggerFactory.getLogger(classOf[AbstractHANAPartitionLoader])

  /**
   * Provides a [[HANAJdbcClient]] implementation used for the partitions loading.
   *
   * @param hanaConfiguration The HANA connection configuration
   * @return a [[HANAJdbcClient]] implementation
   */
  def getHANAJdbcClient(hanaConfiguration: HANAConfig): HANAJdbcClient

  /**
   * Loads a partition of a DataFrame to the HANA backend. This is done in
   * a single database transaction in order to avoid repeatedly inserting
   * data as much as possible.
   *
   * @param connection The HANA JDBC Client Connection
   * @param tableName The name of the table to load
   * @param iterator Iterator over the dataset to load
   * @param metaSchema The SinkRecord schema
   * @param insertMode The insert mode
   * @param batchSize The batch size
   */
  private[hana] def loadPartition(connection: Connection,
                                  tableName: String,
                                  iterator: Iterator[SinkRecord],
                                  metaSchema: MetaSchema,
                                  insertMode: String,
                                  deleteEnabled: Boolean,
                                  batchSize: Int): Unit = {
    //TODO change how preparedStatement usage to not create one every time
    WithCloseables(connection.prepareStatement(prepareInsertIntoStmt(tableName, metaSchema, insertMode))) { stmt =>
        val fieldsValuesConverters = HANAJdbcTypeConverter.getSinkRowDatatypesSetters(metaSchema.fields,
          stmt)
        for (batchRows <- iterator.grouped(batchSize)) {
          for (row <- batchRows) {
            var dataFromKey: Struct = null
            var dataFromValue: Struct = null

            if (row.key() != null && row.key().isInstanceOf[Struct]) {
              dataFromKey = row.key().asInstanceOf[Struct]
            }
            if (row.value() != null && row.value().isInstanceOf[Struct]) {
              dataFromValue = row.value().asInstanceOf[Struct]
            }

            if (dataFromValue == null) {
              if (deleteEnabled) {
                // handle the tombstone record to delete the record when deleteEnabled is true
                if (row.keySchema != null && dataFromKey != null) {
                  // handle delete
                  var keyFields = Seq[metaAttr]()
                  for (field <- row.keySchema.fields.asScala) {
                    val fieldSchema: Schema = field.schema
                    val fieldAttr = metaAttr(field.name(),
                      HANAJdbcTypeConverter.convertToHANAType(fieldSchema), 1, 0, 0, isSigned = false)
                    keyFields = keyFields :+ fieldAttr
                  }
                  //TODO change how preparedStatement usage to not create one every time
                  WithCloseables(connection.prepareStatement(prepareDeleteFromStmt(tableName, row.keySchema))) { dstmt =>
                    val keyFieldsValuesConverters = HANAJdbcTypeConverter.getSinkRowDatatypesSetters(keyFields, dstmt)
                    for (field <- row.keySchema.fields.asScala) {
                      keyFieldsValuesConverters(field.index)(dataFromKey.get(field.name()))
                    }
                    dstmt.execute()
                  }
                } else {
                  // ignore
                  log.warn("Missing schema for the tombstone record")
                }
              }
            } else if (deleteEnabled && metaSchema.fields.exists(f => f.name == "__deleted")
              && dataFromValue.get("__deleted").asInstanceOf[String].toBoolean) {
              // skip the pseudo-delete record when deleteEnabled is true so that is not upserted
              log.info("Skipping update for the to-be-deleted record")
            } else {
              // insert, upsert, pseudo-delete
              metaSchema.fields.zipWithIndex.foreach{
                case (field, i) =>
                  //REVISIT why is dataFromKey looked up first?
                  try {
                    Preconditions.checkArgument(dataFromKey != null && dataFromKey.get(field.name) != null)
                    fieldsValuesConverters(i)(dataFromKey.get(field.name))
                  } catch {
                    case e: Exception =>
                      try {
                        Preconditions.checkArgument(dataFromValue != null && dataFromValue.get(field.name) != null)
                        fieldsValuesConverters(i)(dataFromValue.get(field.name))
                      } catch {
                        case e: Exception =>
                          fieldsValuesConverters(i)(null)
                      }
                  }
                //Handle Null Values
                /*case (null, i) =>
                  stmt.setNull(i + 1, JdbcTypeConverter
                    .convertToHANAType(metaSchema.fields(i).dataType))*/
              }
              stmt.addBatch()
            }
          }
          stmt.executeBatch()
          stmt.clearParameters()
        }
      }

      // conn.commit()
      //committed = true
    }
    /*, doClose = { conn => {
      if (!committed) {
        // Partial commit could have happened....rollback
        conn.rollback()
        conn.close()
      } else {
        /**
         * The stage cannot fail now as failure will re-trigger the inserts.
         * Do not propagate exceptions further.
         */
        log.info("Record inserted")
        try {
          conn.close()
        } catch {
          case e: Exception => log.warn("Transaction succeeded, but closing failed", e)
        }
      }
    }
    })*/

  /**
    * Loads a partition of a DataFrame to the HANA backend. This is done in
    * a single database transaction in order to avoid repeatedly inserting
    * data as much as possible.
    *
    * @param connection The HANA JDBC Client Connection
    * @param collectionName The name of the table to load
    * @param iterator Iterator over the dataset to load
    * @param metaSchema The SinkRecord schema
    * @param insertMode The insert mode
    * @param batchSize The batch size
    */
  private[hana] def loadPartitionForJsonStore(connection: Connection,
                                              collectionName: String,
                                              iterator: Iterator[SinkRecord],
                                              metaSchema: MetaSchema,
                                              insertMode: String,
                                              deleteEnabled: Boolean,
                                              batchSize: Int): Unit = {
    for (batchRows <- iterator.grouped(batchSize)) {
      for (row <- batchRows) {
        //TODO change how preparedStatement usage to not create one every time
        WithCloseables(connection
          .prepareStatement(prepareJsonStatement(connection, collectionName, row, metaSchema, insertMode, batchSize))) { stmt =>
          stmt.execute()
        }
      }
    }
  }

  private[hana] def prepareJsonStatement(connection: Connection,
                                         collectionName: String,
                                         row: SinkRecord,
                                         metaSchema: MetaSchema,
                                         insertMode: String,
                                         batchSize: Int): String = {
    var dataFromKey: Struct = null
    var dataFromValue: Struct = null

    if (row.key() != null && row.key().isInstanceOf[Struct]) {
      dataFromKey = row.key().asInstanceOf[Struct]
    }

    if (row.value() != null && row.value().isInstanceOf[Struct]) {
      dataFromValue = row.value().asInstanceOf[Struct]
    }

    val sinkRecordMap = collection.mutable.Map[String, AnyRef]()
    try {
      Preconditions.checkArgument(dataFromKey != null)

      val fields = dataFromKey.schema().fields()
      fields.forEach(new Consumer[Field] {
        override def accept(field: Field): Unit = {
          sinkRecordMap.update(field.name(), dataFromKey.get(field.name()))
        }
      })
    } catch {
      case e: Exception =>
        try {
          Preconditions.checkArgument(dataFromValue != null)

          val fields = dataFromValue.schema().fields()
          fields.forEach(new Consumer[Field] {
            override def accept(field: Field): Unit = {
              sinkRecordMap.update(field.name(), dataFromValue.get(field.name()))
            }
          })
        } catch {
          case e: Exception =>
            //do nothing
        }
    }

/*    metaSchema.fields.zipWithIndex.foreach {
      case (field, i) =>
        try {
          Preconditions.checkArgument(dataFromKey != null && dataFromKey.get(field.name) != null)
          sinkRecordMap.update(field.name, dataFromKey.get(field.name))
        } catch {
          case e: Exception =>
            try {
              Preconditions.checkArgument(dataFromValue != null && dataFromValue.get(field.name) != null)
              sinkRecordMap.update(field.name, dataFromValue.get(field.name))
            } catch {
              case e: Exception =>
                //do nothing
            }
        }
    }*/
    prepareInsertIntoCollStmt(collectionName, sinkRecordMap.toMap[String, AnyRef], insertMode)
  }


  /**
   * Prepares an INSERT INTO statement for the give parameters.
   *
   * @param fullTableName The fully-qualified name of the table
   * @param metaSchema The Metadata schema
   * @param insertMode The insert mode
   * @return The prepared INSERT INTO statement as a [[String]]
   */
  private[hana] def prepareInsertIntoStmt(fullTableName: String, metaSchema: MetaSchema, insertMode: String): String = {
    val fields = metaSchema.fields
    val columnNames = fields.map(field => s""""${field.name}"""").mkString(", ")
    val placeHolders = fields.map(field => s"""?""").mkString(", ")
    val stmt = insertMode match {
      case BaseConfigConstants.INSERT_MODE_UPSERT =>
        s"""UPSERT $fullTableName ($columnNames) VALUES ($placeHolders) WITH PRIMARY KEY"""
      case _ =>
        s"""INSERT INTO $fullTableName ($columnNames) VALUES ($placeHolders)"""
    }
    log.info(s"Creating prepared statement: $stmt")
    stmt
  }

  /**
    * Prepares an INSERT INTO statement for the give parameters.
    *
    * @param collectionName The fully-qualified name of the collection
    * @param recordMap The collection map
    * @param insertMode The insert mode
    * @return The prepared INSERT INTO statement as a [[String]]
    */
  private[hana] def prepareInsertIntoCollStmt(collectionName: String, recordMap: Map[String, AnyRef], insertMode: String): String = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    val insertCmd = insertMode.toUpperCase
    val stmt = insertMode match {
      case BaseConfigConstants.INSERT_MODE_UPSERT =>
        s"""UPSERT $collectionName VALUES ('${mapper.writeValueAsString(recordMap)}') WITH PRIMARY KEY"""
      case _ =>
        s"""INSERT INTO $collectionName VALUES ('${mapper.writeValueAsString(recordMap)}')"""
    }
    log.info(s"Creating prepared statement: $stmt")
    stmt
  }

  private[hana] def prepareDeleteFromStmt(fullTableName: String, schema: Schema): String = {
    val fields = schema.fields
    val builder = new StringBuilder()
    builder.append(s"DELETE FROM $fullTableName WHERE ")
    for (field <- fields.asScala) {
      if (field.index > 0) {
        builder.append(" AND ")
      }
      builder.append(s""""${field.name}" = ?""")
    }
    val stmt = builder.toString()
    log.info(s"Creating prepared statement: $stmt")
    stmt
  }
}
