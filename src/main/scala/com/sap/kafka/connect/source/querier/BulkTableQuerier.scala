package com.sap.kafka.connect.source.querier

import com.sap.kafka.client.hana.HANAJdbcClient
import com.sap.kafka.connect.config.{BaseConfig, BaseConfigConstants}
import com.sap.kafka.connect.source.SourceConnectorConstants
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.connect.source.SourceRecord

import scala.collection.JavaConverters._

class BulkTableQuerier(mode: String, tableOrQuery: String, tablePartition: Int, topic: String,
                       config: BaseConfig, jdbcClient: Option[HANAJdbcClient])
  extends TableQuerier(mode, tableOrQuery, topic, config, jdbcClient) {
  override def createQueryString(): Unit = {
    mode match {
      case BaseConfigConstants.QUERY_MODE_TABLE =>
        if (tablePartition > 0) {
          queryString = Some(s"select * from $tableName PARTITION($tablePartition)")
        } else {
          queryString = Some(s"select * from $tableName")
        }
      case BaseConfigConstants.QUERY_MODE_SQL =>
        queryString = Some(query)
    }
  }

  override def extractRecords(): List[SourceRecord] = {
    if (resultList.isDefined) {
      resultList.get.map(record => {
        var partition: Map[String, String] = null

        mode match {
          case BaseConfigConstants.QUERY_MODE_TABLE =>
            partition = Map(SourceConnectorConstants.TABLE_NAME_KEY -> tableName)
          case _ => throw new ConfigException(s"Unexpected query mode: $mode")
        }
        new SourceRecord(partition.asJava, null, topic,
          getPartition(tablePartition, topic), record.schema(), record)
      })
    }
    else List()
  }

  override def toString: String = "BulkTableQuerier{" +
    "name='" + tableOrQuery + '\'' +
    ", topic='" + topic + '\'' +
    '}'

  /**
    * if no. of table partition exceeds no. of topic partitions,
    * this just takes the highest available topic partition to write.
    */
  private def getPartition(tablePartition: Int, topic: String): Int = {
    val topicProperties = config.topicProperties(topic)
    val maxPartitions = topicProperties("partition.count").toInt
    tablePartition % maxPartitions
  }
}