package com.sap.kafka.connect.sink.hana

import java.sql.Connection
import java.util

import com.google.common.base.Function
import com.google.common.collect.Multimaps
import com.sap.kafka.client.hana.HANAJdbcClient
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.connect.sink.BaseWriter
import org.apache.kafka.connect.sink.SinkRecord
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._


class HANAWriter(config: HANAConfig, hanaClient: HANAJdbcClient,
                 tableCache: scala.collection.mutable.Map[String, HANASinkRecordsCollector])
  extends BaseWriter {

  private val log: Logger = LoggerFactory.getLogger(getClass)
  private var connection:Connection = null

  override def initializeConnection(): Unit = {
    if (connection != null && !connection.isValid(120)) {
      connection.close()
    }
    if(connection == null || connection.isClosed ) {
      connection = hanaClient.getConnection
    }
  }


  override def write(records: util.Collection[SinkRecord]): Unit = {
    log.info("write records to HANA")
    log.info("initialize connection to HANA")

    initializeConnection()

    val topicMap = Multimaps.index(records, new Function[SinkRecord, String] {
      override def apply(sinkRecord: SinkRecord) = sinkRecord.topic()
    }).asMap()

    for ((topic, recordsPerTopic) <- topicMap.asScala) {
      var table = config.topicProperties(topic).get("table.name").get
      if (table.contains("${topic}")) {
        table = table.replace("${topic}", topic)
      }

      val recordsCollector: Option[HANASinkRecordsCollector] = tableCache.get(table)

      recordsCollector match {
        case None =>
          val tableRecordsCollector = new HANASinkRecordsCollector(table, hanaClient, connection, config)
          tableCache.put(table, tableRecordsCollector)
          tableRecordsCollector.add(collectionAsScalaIterableConverter(recordsPerTopic).asScala.toSeq)
        case Some(tableRecordsCollector) =>
          if (config.autoSchemaUpdateOn) {
            tableRecordsCollector.tableConfigInitialized = false
          }
          tableRecordsCollector.add(collectionAsScalaIterableConverter(recordsPerTopic).asScala.toSeq)
      }
    }
    if (!tableCache.isEmpty) {
      flush(tableCache.toMap)
      log.info("flushing records to HANA successful")
    }
  }

  private def flush(tableCache: Map[String, HANASinkRecordsCollector]): Unit = {
    log.info("flush records into HANA")
    for ((table, recordsCollector) <- tableCache) {
        recordsCollector.flush()
    }
    hanaClient.commit(connection)
  }

}
