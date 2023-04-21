package com.sap.kafka.connect.sink.hana

import java.sql.Connection
import com.sap.kafka.client.hana.{HANAConfigInvalidInputException, HANAConfigMissingException, HANAJdbcClient}
import com.sap.kafka.client.{MetaSchema, metaAttr}
import com.sap.kafka.connect.config.BaseConfigConstants
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.schema.KeyValueSchema
import com.sap.kafka.utils.hana.HANAJdbcTypeConverter
import com.sap.kafka.utils.SchemaNotMatchedException
import org.apache.kafka.connect.data.Schema.Type
import org.apache.kafka.connect.data.{Field, Schema}
import org.apache.kafka.connect.sink.SinkRecord
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.collection.mutable


class HANASinkRecordsCollector(var tableName: String, client: HANAJdbcClient,
                               config: HANAConfig) {
  private val log: Logger = LoggerFactory.getLogger(classOf[HANASinkTask])
  private var records: Seq[SinkRecord] = Seq[SinkRecord]()
  private var tableMetaData:Seq[metaAttr] = Seq[metaAttr]()
  private var metaSchema: MetaSchema = null
  var tableConfigInitialized = false


  private def initTableConfig(nameSpace: Option[String], tableName: String, topic: String) : Boolean = {

    tableConfigInitialized match {
      case false =>
        if (config.topicProperties(topic)("table.type") == BaseConfigConstants.COLLECTION_TABLE_TYPE) {
          if (client.collectionExists(tableName)) {
            tableMetaData = client.getMetaData(tableName, nameSpace)
            metaSchema = new MetaSchema(tableMetaData, null)
            tableConfigInitialized = true
          }
        } else if(client.tableExists(nameSpace, tableName)){
          tableMetaData = client.getMetaData(tableName, nameSpace)
          metaSchema = new MetaSchema(tableMetaData, null)
          tableConfigInitialized = true
        }
      case true =>
    }
    tableConfigInitialized
  }

  private[sink] def add(records: Seq[SinkRecord]): Unit = {
    val recordHead = records.head
    val recordSchema = KeyValueSchema(recordHead.keySchema(), recordHead.valueSchema())

    if (recordHead.valueSchema == null && recordHead.value == null) {
      // skip the delete marker
      return
    }
    val allFields = mutable.Set[String]()
    initTableConfig(getTableName._1,getTableName._2, recordHead.topic()) match {
      case true =>
        log.info(s"""Table $tableName exists. Validate the schema and check if schema needs to evolve""")
        var recordFields = Seq[metaAttr]()
        //REVISIT we should cache keySchema and valueSchema at the collector to perform a quick comparison
        // we build recordFields for valueSchema to compare it against metaSchema retrieved from the table
        if (recordSchema.keySchema != null && recordSchema.keySchema.`type` == Type.STRUCT) {
          for (field <- recordSchema.keySchema.fields.asScala) {
            allFields.add(field.name)
            val fieldSchema: Schema = field.schema
            val fieldAttr = metaAttr(field.name,
              HANAJdbcTypeConverter.convertToHANAType(fieldSchema), 1, 0, 0, isSigned = false)
            recordFields = recordFields :+ fieldAttr
          }
        }

        if (recordSchema.valueSchema != null && recordSchema.valueSchema.`type` == Type.STRUCT) {
          for (field <- recordSchema.valueSchema.fields.asScala) {
            if (!allFields.contains(field.name)) {
              val fieldSchema: Schema = field.schema
              val fieldAttr = metaAttr(field.name(),
                HANAJdbcTypeConverter.convertToHANAType(fieldSchema), 1, 0, 0, isSigned = false)
              recordFields = recordFields :+ fieldAttr
            }
          }
        }
        if (config.topicProperties(recordHead.topic())("table.type") != BaseConfigConstants.COLLECTION_TABLE_TYPE) {
          compareSchema(recordFields) match {
            case Some(alts) => if (!alts.isEmpty) {
              if (config.autoEvolve) {
                val altsMetaSchema = new MetaSchema(Seq[metaAttr](), Seq[Field]())
                for (altfield <- alts) {
                  val field = recordSchema.valueSchema.field(altfield.name)
                  metaSchema.fields :+= altfield
                  altsMetaSchema.fields :+= altfield
                  altsMetaSchema.avroFields :+= field
                }
                client.alterTable(getTableName._1, getTableName._2, altsMetaSchema)
              } else {
                val errmsg = s"""Table $tableName has a different schema from the record Schema.
                                |Set 'auto.evolve' parameter to true""".stripMargin
                log.error(errmsg)
                throw new SchemaNotMatchedException(errmsg)
              }
            }
            case None =>
              val errmsg = s"""Table $tableName has an incompatible schema to the record Schema.
                            |Auto Evolution of schema is not supported""".stripMargin
              log.error(errmsg)
              throw new SchemaNotMatchedException(errmsg)
          }
        }
      case false =>
        if (config.autoCreate) {
          // find table type
          val tableType = config.topicProperties(recordHead.topic()).get("table.type").get == BaseConfigConstants.COLUMN_TABLE_TYPE
          // find partition type
          val partitionType = config.topicProperties(recordHead.topic())
            .get("table.partition.mode").get

          //find no. of partitions
          val partitionCount = config.topicProperties(recordHead.topic())
            .get("table.partition.count").get

          metaSchema = new MetaSchema(Seq[metaAttr](), Seq[Field]())

          if (recordSchema.keySchema != null && recordSchema.keySchema.`type` == Type.STRUCT) {
            for (field <- recordSchema.keySchema.fields.asScala) {
              allFields.add(field.name)
              val fieldSchema: Schema = field.schema
              val fieldAttr = metaAttr(field.name,
                HANAJdbcTypeConverter.convertToHANAType(fieldSchema), 1, 0, 0, isSigned = false)
              metaSchema.fields = metaSchema.fields :+ fieldAttr
              metaSchema.avroFields = metaSchema.avroFields :+ field
            }
          }

          if (recordSchema.valueSchema != null && recordSchema.valueSchema.`type` == Type.STRUCT) {
            for (field <- recordSchema.valueSchema.fields.asScala) {
              if (!allFields.contains(field.name)) {
                val fieldSchema: Schema = field.schema
                val fieldAttr = metaAttr(field.name(),
                  HANAJdbcTypeConverter.convertToHANAType(fieldSchema), 1, 0, 0, isSigned = false)
                metaSchema.fields = metaSchema.fields :+ fieldAttr
                metaSchema.avroFields = metaSchema.avroFields :+ field
              }
            }
          }

          if (config.topicProperties(recordHead.topic()).get("pk.mode").get
            == BaseConfigConstants.RECORD_KEY) {
            val keys = getValidKeys(config.topicProperties(recordHead.topic())
              .get("pk.fields").get.split(",").toList, metaSchema.avroFields)

            client.createTable(getTableName._1, getTableName._2, metaSchema,
              config.batchSize, tableType, keys, partitionType, partitionCount.toInt)
          }
          else if (config.topicProperties(recordHead.topic()).get("pk.mode").get
            == BaseConfigConstants.RECORD_VALUE) {
            val keys = getValidKeys(config.topicProperties(recordHead.topic())
              .get("pk.fields").get.split(",").toList, metaSchema.avroFields)

            client.createTable(getTableName._1, getTableName._2, metaSchema,
              config.batchSize, tableType, keys, partitionType, partitionCount.toInt)
          } else {
            if (config.topicProperties(recordHead.topic())("table.type") == BaseConfigConstants.COLLECTION_TABLE_TYPE) {
              client.createCollection(getTableName._2)
            } else {
              client.createTable(getTableName._1, getTableName._2,
                metaSchema, config.batchSize, tableType, partitionType = partitionType,
                partitionCount = partitionCount.toInt)
            }
          }
          client.getMetaData(getTableName._2,getTableName._1)
        } else {
          throw new HANAConfigMissingException(s"Table does not exist. Set 'auto.create' parameter to true")
        }
    }
    this.records = records
  }

  private[sink] def flush(connection: Connection): Seq[SinkRecord] = {
    val flushedRecords = records
    if (!records.isEmpty) {
      val insertMode = config.topicProperties(records.head.topic())("insert.mode")
      val deleteEnabled = config.topicProperties(records.head.topic())("delete.enabled").toBoolean
      if (config.topicProperties(records.head.topic())("table.type") == BaseConfigConstants.COLLECTION_TABLE_TYPE) {
        client.loadData(getTableName._2, connection, metaSchema, records, insertMode, deleteEnabled, config.batchSize)
      } else {
        client.loadData(getTableName._1, getTableName._2, connection, metaSchema, records, insertMode, deleteEnabled, config.batchSize)
      }
      records = Seq.empty[SinkRecord]
    }
    flushedRecords
  }

  private[sink] def size(): Int = {
    records.size
  }

  private def getTableName: (Option[String], String) = {
    tableName match {
      case BaseConfigConstants.TABLE_NAME_FORMAT(schema, table) =>
        (Some(schema), table)
      case BaseConfigConstants.COLLECTION_NAME_FORMAT(table) =>
        (None, table)
      case _ =>
        throw new HANAConfigInvalidInputException(s"The table name mentioned in `{topic}.table.name` is invalid." +
          s" Does not follow naming conventions")
    }
  }

  private def getValidKeys(keys: List[String], allFields: Seq[Field]): List[String] = {
    val fields = allFields.map(metaAttr => metaAttr.name())
    keys.filter(key => fields.contains(key))
  }

  // Compares the specified schema against the current schema and returns an empty set, a set with some fields, or None.
  // An empty set if the specified schema is fully compatible with the current schema
  // (no unknown fields appear in the specified schema, all nonNullable fields of the current schema
  //  appear in the specified schema, the field data types are identical)
  // A set with some fields if the specified schema compatible except there are additional nullable fields
  // None if the specifled schema is incompatible
  private def compareSchema(dbSchema : Seq[metaAttr]): Option[Set[metaAttr]] = {
    val fieldNames = metaSchema.fields.map(_.name).toSet
    val fieldTypes = metaSchema.fields.map(a => a.name -> a.dataType).toMap
    var nonNullables = metaSchema.fields.filter(a => a.isNullable == 0).map(_.name).toSet
    var alts = Seq[metaAttr]()
    for (field <- dbSchema) {
      if (!fieldNames.contains(field.name)) {
        alts :+= field
      } else if (!isCompatibleType(field.dataType, fieldTypes.get(field.name).get)) {
        // incompatible as the datatype is not compatible
        log.info(s"Field ${field.name} with target type ${field.dataType} is incompatile with source type ${fieldTypes.get(field.name).get}")
        return None
      }
      if (nonNullables.contains(field.name)) {
        nonNullables -= field.name
      }
    }
    if (!nonNullables.isEmpty) {
      // incompatible as some nonNullables are not set
      log.info(s"Non-nullable fields $nonNullables are missing")
      return None
    }
    // compatible
    return Some(alts.toSet)
  }

  private def isCompatibleType(tgttype: Int, srctype: Int): Boolean = {
    // identical types
    if (tgttype == srctype) {
      return true
    }
    // string, boolean types
    if ((isStringType(tgttype) && isStringType(srctype))
      || (isBooleanType(tgttype) && isBooleanType(srctype))) {
      return true
    }

    // TODO add numeric types?
    return false
  }

  private def isStringType(sqltype: Int): Boolean = {
    sqltype match {
      case java.sql.Types.CHAR | java.sql.Types.VARCHAR | java.sql.Types.LONGNVARCHAR
           | java.sql.Types.NCHAR | java.sql.Types.NVARCHAR | java.sql.Types.CLOB  | java.sql.Types.NCLOB
           | java.sql.Types.DATALINK | java.sql.Types.SQLXML => true
      case _ => false
    }
  }

  private def isBooleanType(sqltype: Int): Boolean = {
    sqltype match {
      case java.sql.Types.BIT | java.sql.Types.BOOLEAN => true
      case _ => false
    }
  }
}
