package com.sap.kafka.connect.source.hana

import java.util
import com.sap.kafka.connect.source.hana.HANASourceTask
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.source.SourceConnector

import scala.collection.JavaConverters._

class HANASourceConnector extends SourceConnector {
  private var configProperties: Option[util.Map[String, String]] = None

  override def version(): String = getClass.getPackage.getImplementationVersion

  override def start(properties: util.Map[String, String]): Unit = {
    configProperties = Some(properties)
  }

  override def taskClass(): Class[_ <: Task] = classOf[HANASourceTask]

  override def taskConfigs(maxTasks: Int): util.List[util.Map[String, String]] = {
    (1 to maxTasks).map(c => configProperties.get).toList.asJava
  }

  override def stop(): Unit = {

  }

  override def config(): ConfigDef = {
    new ConfigDef
  }
}