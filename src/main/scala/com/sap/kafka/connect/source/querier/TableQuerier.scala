package com.sap.kafka.connect.source.querier

import com.sap.kafka.client.hana.HANAJdbcClient
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.connect.config.{BaseConfig, BaseConfigConstants}
import com.sap.kafka.utils.hana.HANAJdbcTypeConverter
import org.apache.kafka.connect.data.{Schema, Struct}
import org.apache.kafka.connect.source.SourceRecord
import org.slf4j.LoggerFactory

abstract class TableQuerier(mode: String, tableOrQuery: String,
                            topic: String, config: BaseConfig,
                            var jdbcClient: Option[HANAJdbcClient])
                extends Comparable[TableQuerier] {
  var tableName: String = if (mode.equals(BaseConfigConstants.QUERY_MODE_TABLE)) tableOrQuery else null
  var query: String = if (mode.equals(BaseConfigConstants.QUERY_MODE_SQL)) tableOrQuery else null

  var lastUpdate: Long = 0
  var schema: Schema = _
  var queryString: Option[String] = None
  var resultList: Option[List[Struct]] = None
  var maxRowsOffset: Int = 0

  val log = LoggerFactory.getLogger(getClass)

  def getLastUpdate(): Long = lastUpdate

  def getMaxRowsOffset(): Int = maxRowsOffset

  def getOrCreateQueryString(): Option[String] = {
    createQueryString()
    queryString
  }

  def createQueryString(): Unit

  def querying(): Boolean = resultList.isDefined

  def maybeStartQuery(): Unit = {
    if (resultList.isEmpty) {
      schema = getSchema()
      // update the query when there is no left over records from the previous query
      if (maxRowsOffset == 0) {
        queryString = getOrCreateQueryString()
      }

      val batchMaxRows = config.batchMaxRows
      resultList = getOrCreateJdbcClient().get.executeQuery(schema, queryString.get,
        maxRowsOffset, batchMaxRows)
      // if the result list has the batchMaxRows, assume there are some more rows to read
      if (!resultList.isEmpty && resultList.get.size == batchMaxRows) {
        maxRowsOffset += batchMaxRows
      } else {
        maxRowsOffset = 0
      }
      log.info(resultList.size.toString)
    }
  }

  def extractRecords(): List[SourceRecord]

  def close(now: Long): Unit = {
    resultList = None
    schema = null

    lastUpdate = now
  }

  protected def getOrCreateJdbcClient(): Option[HANAJdbcClient] = {
    if (jdbcClient.isDefined) {
      return jdbcClient
    }

    config match {
      case hanaConfig: HANAConfig => Some(HANAJdbcClient(hanaConfig))
      case _ => throw new RuntimeException("Cannot create Jdbc Client")
    }
  }

  private def getSchema(): Schema = {
    val options = Map("numeric.mapping" -> config.numericMapping)
    mode match {
      case BaseConfigConstants.QUERY_MODE_TABLE =>
        if (getOrCreateJdbcClient().get.isInstanceOf[HANAJdbcClient]) {
          val metadata = getOrCreateJdbcClient().get.getMetaData(tableOrQuery, None)
          HANAJdbcTypeConverter.convertHANAMetadataToSchema(tableName, metadata, options)
        } else {
          throw new RuntimeException("Jdbc Client is not available")
        }
      case BaseConfigConstants.QUERY_MODE_SQL =>
        if (getOrCreateJdbcClient().get.isInstanceOf[HANAJdbcClient]) {
          val metadata = getOrCreateJdbcClient().get.getMetadata(tableOrQuery)
          HANAJdbcTypeConverter.convertHANAMetadataToSchema(topic, metadata, options)
        } else {
          throw new RuntimeException("Jdbc Client is not available")
        }
      case _ =>
        throw new RuntimeException("Other Query modes are not supported")
    }
  }

  override def compareTo(other: TableQuerier): Int = {
    if (this.lastUpdate < other.lastUpdate) {
      -1
    } else if (this.lastUpdate > other.lastUpdate) {
      0
    } else {
      this.tableName.compareTo(other.tableName)
    }
  }
}
