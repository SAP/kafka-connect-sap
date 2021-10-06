package com.sap.kafka.connect.source

import java.util
import java.util.concurrent.atomic.AtomicBoolean
import com.sap.kafka.client.hana.{HANAConfigMissingException, HANAJdbcClient}
import com.sap.kafka.connect.config.hana.HANAParameters
import com.sap.kafka.connect.config.{BaseConfig, BaseConfigConstants}
import com.sap.kafka.connect.source.querier.{BulkTableQuerier, IncrColTableQuerier, TableQuerier}
import com.sap.kafka.utils.ExecuteWithExceptions
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.utils.{SystemTime, Time}
import org.apache.kafka.connect.source.{SourceRecord, SourceTask}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

import scala.collection.mutable

abstract class GenericSourceTask extends SourceTask {
  protected var configRawProperties: Option[util.Map[String, String]] = None
  protected var config: BaseConfig = _
  private val tableQueue = new mutable.Queue[TableQuerier]()
  protected var time: Time = new SystemTime()

  private var stopFlag: AtomicBoolean = _
  // HANAJdbcClient to be replaced by BaseJdbcClient once it is created.
  protected var jdbcClient: HANAJdbcClient = _
  private val log = LoggerFactory.getLogger(getClass)

  override def start(props: util.Map[String, String]): Unit = {
    log.info("Read records from HANA")
    configRawProperties = Some(props)

    ExecuteWithExceptions[Unit, ConfigException, HANAConfigMissingException] (
      new HANAConfigMissingException("Couldn't start HANASourceTask due to configuration error")) { () =>
      config = HANAParameters.getConfig(props)
    }

    if (jdbcClient == null) {
      jdbcClient = createJdbcClient()
    }

    val topics = config.topics

    val queryMode = config.queryMode
    val tableOrQueryInfos = getTableOrQueryInfos()

    val mode = config.mode
    var offsets: util.Map[util.Map[String, String], util.Map[String, Object]] = null
    var incrementingCols: List[String] = List()

    if (mode.equals(BaseConfigConstants.MODE_INCREMENTING)) {
      val partitions =
        new util.ArrayList[util.Map[String, String]](tableOrQueryInfos.length)

      queryMode match {
        case BaseConfigConstants.QUERY_MODE_TABLE =>
          tableOrQueryInfos.foreach(tableInfo => {
            val partition = new util.HashMap[String, String]()
            partition.put(SourceConnectorConstants.TABLE_NAME_KEY, s"${tableInfo._1}${tableInfo._2}")
            partitions.add(partition)
            incrementingCols :+= config.topicProperties(tableInfo._3)("incrementing.column.name")
          })
        case BaseConfigConstants.QUERY_MODE_SQL =>
          tableOrQueryInfos.foreach(queryInfo => {
            val partition = new util.HashMap[String, String]()
            partition.put(SourceConnectorConstants.QUERY_NAME_KEY, queryInfo._1)
            partitions.add(partition)
            incrementingCols :+= config.topicProperties(queryInfo._3)("incrementing.column.name")
          })

      }
      offsets = context.offsetStorageReader().offsets(partitions)
    }

    var count = 0
    tableOrQueryInfos.foreach(tableOrQueryInfo => {
      val partition = new util.HashMap[String, String]()
      queryMode match {
        case BaseConfigConstants.QUERY_MODE_TABLE =>
          partition.put(SourceConnectorConstants.TABLE_NAME_KEY, s"${tableOrQueryInfo._1}${tableOrQueryInfo._2}")
        case BaseConfigConstants.QUERY_MODE_SQL =>
          partition.put(SourceConnectorConstants.QUERY_NAME_KEY, tableOrQueryInfo._1)
        case _ =>
          throw new ConfigException(s"Unexpected Query Mode: $queryMode")
      }

      val offset = if (offsets == null) null else offsets.get(partition)

      if (mode.equals(BaseConfigConstants.MODE_BULK)) {
        tableQueue += new BulkTableQuerier(queryMode, tableOrQueryInfo._1, tableOrQueryInfo._2, tableOrQueryInfo._3,
          config, Some(jdbcClient))
      } else if (mode.equals(BaseConfigConstants.MODE_INCREMENTING)) {
        tableQueue += new IncrColTableQuerier(queryMode, tableOrQueryInfo._1, tableOrQueryInfo._2, tableOrQueryInfo._3,
          incrementingCols(count),
          if (offset == null) null else offset.asScala.toMap,
          config, Some(jdbcClient))
      }
      count += 1
    })
    stopFlag = new AtomicBoolean(false)
  }

  override def stop(): Unit = {
    if (stopFlag != null) {
      stopFlag.set(true)
    }
  }

  override def poll(): util.List[SourceRecord] = {
    log.info("Start polling records from HANA")
    val topic = config.topics.head


    while (!stopFlag.get()) {
      var now = time.milliseconds()
      val querier = tableQueue.head

      if (!querier.querying()) {
        if (querier.getMaxRowsOffset() == 0) {
          val nextUpdate = querier.getLastUpdate() +
            config.topicProperties(topic)("poll.interval.ms").toInt
          val untilNext = nextUpdate - now

          if (untilNext > 0) {
            log.info(s"Waiting $untilNext ms to poll from ${querier.toString}")
            time.sleep(untilNext)
            // return from the poll to check the task status
            return null
          }
        }
      }
      var results = List[SourceRecord]()

      log.info(s"Checking for the next block of results from ${querier.toString}")
      querier.maybeStartQuery()

      results ++= querier.extractRecords()

      // dequeue the queierer only if the records are fully polled
      if (querier.getMaxRowsOffset() == 0) {
        val removedQuerier = tableQueue.dequeue()
        assert(removedQuerier == querier)
      }
      log.info(s"Closing this query for ${querier.toString}")
      querier.close(time.milliseconds())
      if (querier.getMaxRowsOffset() == 0) {
        tableQueue.enqueue(querier)
      }

      if (results.isEmpty) {
        log.info(s"No updates for ${querier.toString}")
        return null
      }

      log.info(s"Returning ${results.size} records for ${querier.toString}")
      return results.asJava
    }
    null
  }

  def getTableOrQueryInfos(): List[Tuple3[String, Int, String]] = {
    val props = configRawProperties.get
    props.asScala.filter(p => p._1.startsWith("_tqinfos.") && p._1.endsWith(".name")).map(
      t => Tuple3(
        t._2,
        props.get(t._1.replace("name", "partition")).toInt,
        props.get(t._1.replace("name", "topic")))).toList
  }

  protected def createJdbcClient(): HANAJdbcClient
}
