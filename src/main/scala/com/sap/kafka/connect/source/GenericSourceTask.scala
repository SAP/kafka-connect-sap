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
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.{SourceRecord, SourceTask}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

abstract class GenericSourceTask extends SourceTask {
  protected var config: BaseConfig = _
  private val tableQueue = new mutable.Queue[TableQuerier]()
  protected var time: Time = new SystemTime()

  private var stopFlag: AtomicBoolean = _
  // HANAJdbcClient to be replaced by BaseJdbcClient once it is created.
  protected var jdbcClient: HANAJdbcClient = _
  private val log = LoggerFactory.getLogger(getClass)

  override def start(props: util.Map[String, String]): Unit = {
    log.info("Read records from HANA")

    ExecuteWithExceptions[Unit, ConfigException, HANAConfigMissingException] (
      new HANAConfigMissingException("Couldn't start HANASourceTask due to configuration error")) { () =>
      config = HANAParameters.getConfig(props)
    }

    if (jdbcClient == null) {
      jdbcClient = createJdbcClient()
    }

    val topics = config.topics

    var tables: List[(String, String)] = Nil
    if (topics.forall(topic => config.topicProperties(topic).keySet.contains("table.name"))) {
      tables = topics.map(topic =>
        (config.topicProperties(topic)("table.name"), topic))
    }

    var query: List[(String, String)] = Nil
    if (topics.forall(topic => config.topicProperties(topic).keySet.contains("query"))) {
      query = topics.map(topic =>
        (config.topicProperties(topic)("query"), topic))
    }

    if (tables.isEmpty && query.isEmpty) {
      throw new ConnectException("Invalid configuration: each HANASourceTask must have" +
        " one table assigned to it")
    }

    val queryMode = config.queryMode

    val tableOrQueryInfos = queryMode match {
                              case BaseConfigConstants.QUERY_MODE_TABLE =>
                                getTables(tables)
                              case BaseConfigConstants.QUERY_MODE_SQL =>
                                getQueries(query)
                            }

    val mode = config.mode
    var offsets: util.Map[util.Map[String, String], util.Map[String, Object]] = null
    var incrementingCols: List[String] = List()

    if (mode.equals(BaseConfigConstants.MODE_INCREMENTING)) {
      val partitions =
        new util.ArrayList[util.Map[String, String]](tables.length)

      queryMode match {
        case BaseConfigConstants.QUERY_MODE_TABLE =>
          tableOrQueryInfos.foreach(tableInfo => {
            val partition = new util.HashMap[String, String]()
            partition.put(SourceConnectorConstants.TABLE_NAME_KEY, tableInfo._3)
            partitions.add(partition)
            incrementingCols :+= config.topicProperties(tableInfo._4)("incrementing.column.name")
          })
        case BaseConfigConstants.QUERY_MODE_SQL =>
          tableOrQueryInfos.foreach(queryInfo => {
            val partition = new util.HashMap[String, String]()
            partition.put(SourceConnectorConstants.QUERY_NAME_KEY, queryInfo._1)
            partitions.add(partition)
            incrementingCols :+= config.topicProperties(queryInfo._4)("incrementing.column.name")
          })

      }
      offsets = context.offsetStorageReader().offsets(partitions)
    }

    var count = 0
    tableOrQueryInfos.foreach(tableOrQueryInfo => {
      val partition = new util.HashMap[String, String]()
      queryMode match {
        case BaseConfigConstants.QUERY_MODE_TABLE =>
          partition.put(SourceConnectorConstants.TABLE_NAME_KEY, tableOrQueryInfo._3)
        case BaseConfigConstants.QUERY_MODE_SQL =>
          partition.put(SourceConnectorConstants.QUERY_NAME_KEY, tableOrQueryInfo._1)
        case _ =>
          throw new ConfigException(s"Unexpected Query Mode: $queryMode")
      }

      val offset = if (offsets == null) null else offsets.get(partition)

      val topic = tableOrQueryInfo._4

      if (mode.equals(BaseConfigConstants.MODE_BULK)) {
        tableQueue += new BulkTableQuerier(queryMode, tableOrQueryInfo._1, tableOrQueryInfo._2, topic,
          config, Some(jdbcClient))
      } else if (mode.equals(BaseConfigConstants.MODE_INCREMENTING)) {
        tableQueue += new IncrColTableQuerier(queryMode, tableOrQueryInfo._1, tableOrQueryInfo._2, topic,
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

    var now = time.milliseconds()

    while (!stopFlag.get()) {
      val querier = tableQueue.head

      var waitFlag = false
      if (!querier.querying()) {
        val nextUpdate = querier.getLastUpdate() +
          config.topicProperties(topic)("poll.interval.ms").toInt
        val untilNext = nextUpdate - now

        if (untilNext > 0) {
          log.info(s"Waiting $untilNext ms to poll from ${querier.toString}")
          waitFlag = true
          time.sleep(untilNext)
          now = time.milliseconds()
        }
      }

      if (!waitFlag) {
        var results = List[SourceRecord]()

        log.info(s"Checking for the next block of results from ${querier.toString}")
        querier.maybeStartQuery()

        results ++= querier.extractRecords()

        log.info(s"Closing this query for ${querier.toString}")
        val removedQuerier = tableQueue.dequeue()
        assert(removedQuerier == querier)
        now = time.milliseconds()
        querier.close(now)
        tableQueue.enqueue(querier)

        if (results.isEmpty) {
          log.info(s"No updates for ${querier.toString}")
          return null
        }

        log.info(s"Returning ${results.size} records for ${querier.toString}")
        return results.asJava
      }
    }
    null
  }

  protected def getTables(tables: List[Tuple2[String, String]])
  : List[Tuple4[String, Int, String, String]]

  protected def getQueries(query: List[(String, String)])
  : List[Tuple4[String, Int, String, String]]

  protected def createJdbcClient(): HANAJdbcClient
}