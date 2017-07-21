package com.sap.kafka.connect.sink.hana

import java.util

import com.sap.kafka.client.hana.HANAJdbcClient
import com.sap.kafka.connect.config.hana.{HANAParameters, HANAConfig}
import com.sap.kafka.connect.config.BaseConfig
import com.sap.kafka.connect.sink.{BaseWriter, GenericSinkTask}
import org.slf4j.LoggerFactory


class HANASinkTask extends GenericSinkTask {

   log = LoggerFactory.getLogger(classOf[HANASinkTask])
  private val tableCache = scala.collection.mutable.Map[String, HANASinkRecordsCollector]()
  var hanaClient: HANAJdbcClient = _

  override def start(props: util.Map[String, String]): Unit = {
    log.info("Starting Kafka-Connect task")
    config = HANAParameters.getConfig(props)
    hanaClient = new HANAJdbcClient(config.asInstanceOf[HANAConfig])
    initWriter(config.asInstanceOf[HANAConfig])
  }

  override def initWriter(config: BaseConfig): BaseWriter = {
    log.info("init HANA Writer for writing the records")
    writer = new HANAWriter(config.asInstanceOf[HANAConfig], hanaClient, tableCache)
    writer
  }


}
