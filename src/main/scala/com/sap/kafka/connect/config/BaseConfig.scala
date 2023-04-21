package com.sap.kafka.connect.config

import org.slf4j.{Logger, LoggerFactory}


abstract class BaseConfig(props: Map[String, String]) {
  private val log: Logger = LoggerFactory.getLogger(classOf[BaseConfig])

  /**
    * DB Jdbc user for source & sink
    */
  def connectionUser = props("connection.user")

  /**
    * DB Jdbc password for source & sink
    */
  def connectionPassword = props("connection.password")

  /**
    * List of topics to be published or subscribed by source or sink
    */
  def topics = props("topics").toString.split(",").toList

  /**
    * Batch size for sink. Should be an integer.
    * Default is 3000.
    */
  def batchSize = props.getOrElse[String]("batch.size", "3000").toInt

  /**
    * Max retries for sink. Should be an integer.
    * Default is 10.
    */
  @deprecated("Retries are handled by the task","20-04-2023")
  def maxRetries = props.getOrElse[String]("max.retries", "1").toInt

  /**
    * Auto create HANA tables if table is not found for Sink.
    * Default is false.
    */
  def autoCreate = props.getOrElse[String]("auto.create", "false").toBoolean

  /**
    * Auto evolve HANA tables if table requires some changes to match the record for Sink.
    * Default is false.
    */
  def autoEvolve = props.getOrElse[String]("auto.evolve", "false").toBoolean

  /**
    * Max rows to include in a single batch call for source. Should be an integer.
    * Default is 100.
    */
  def batchMaxRows = props.getOrElse[String]("batch.max.rows", "100").toInt

  /**
    * Query mode for polling data from table for source.
    */
  def queryMode = props.getOrElse[String]("queryMode", BaseConfigConstants.QUERY_MODE_TABLE)

  /**
    * The mode for updating a table each time it is polled.
    * Default is 'bulk'.
    */
  def mode = props.getOrElse[String]("mode", BaseConfigConstants.MODE_BULK)

  /**
    * Whether to auto update Schema from Database for every record flush for sink
    * Default is 'false'.
    */
  def autoSchemaUpdateOn = props.getOrElse[String]("auto.schema.update", "false").toBoolean

  /**
   * Specifies how the numeric type is mapped.
   * Default is 'none'
   */
  def numericMapping: String = props.getOrElse[String]("numeric.mapping", BaseConfigConstants.NUMERIC_MAPPING_NONE)

  def topicProperties(topic: String) = {
    val topicPropMap = scala.collection.mutable.Map[String, String]()

    for ((key, value) <- props) {
      /**
        * table name to fetch from or write to by source & sink
        */
      if (key == s"$topic.table.name") {
        topicPropMap.put("table.name", value)
      }

      /**
        * query to fetch from by source
        */
      if (key == s"$topic.query") {
        topicPropMap.put("query", value)
      }

      /**
        * primary key mode to be used by sink.
        * Default is none.
        */
      if (key == s"$topic.pk.mode") {
        topicPropMap.put("pk.mode", value)
      }

      /**
        * comma-separated primary key fields to be used by sink
        */
      if (key == s"$topic.pk.fields") {
        topicPropMap.put("pk.fields", value)
      }

      /**
        * poll interval time to be used by source
        * Default value is 60000
        */
      if (key == s"$topic.poll.interval.ms") {
        topicPropMap.put("poll.interval.ms", value)
      }

      /**
        * incrementing column name to be used by source
        */
      if (key == s"$topic.incrementing.column.name") {
        topicPropMap.put("incrementing.column.name", value)
      }

      /**
        * topic partition count to be used by source.
        * Default value is 1.
        */
      if (key == s"$topic.partition.count") {
        topicPropMap.put("partition.count", value)
      }

      /**
        * table partition mode to be used by sink
        * Default value is none.
        */
      if (key == s"$topic.table.partition.mode") {
        value match {
          case BaseConfigConstants.ROUND_ROBIN_PARTITION | BaseConfigConstants.HASH_PARTITION =>
            topicPropMap.put("table.partition.mode", value)
          case _ => log.warn(s"Ignoring invalid table.partition.mode $value")
        }
      }

      /**
        * table partition count to be used by sink
        * Default value is 0.
        */
      if (key == s"$topic.table.partition.count") {
        topicPropMap.put("table.partition.count", value)
      }

      /**
        * insert mode to be used by sink.
        * Default is insert.
        */
      if (key == s"$topic.insert.mode") {
        value match {
          case BaseConfigConstants.INSERT_MODE_INSERT | BaseConfigConstants.INSERT_MODE_UPSERT =>
            topicPropMap.put("insert.mode", value)
          case _ => log.warn(s"Ignoring invalid insert.mode $value")
        }
      }

      if (key == s"$topic.delete.enabled") {
        value match {
          case "true" | "false" =>
            topicPropMap.put("delete.enabled", value)
          case _ =>
            log.warn(s"Ignoring invalid delete.enabled $value")
        }
      }

    }

    if (topicPropMap.get("pk.mode").isEmpty) {
      topicPropMap.put("pk.mode", "none")
    }

    if (topicPropMap.get("pk.fields").isEmpty) {
      topicPropMap.put("pk.fields", "")
    }

    if (topicPropMap.get("poll.interval.ms").isEmpty) {
      topicPropMap.put("poll.interval.ms", "60000")
    }


    if (topicPropMap.get("table.partition.mode").isEmpty) {
      topicPropMap.put("table.partition.mode", BaseConfigConstants.NO_PARTITION)
    }

    if (topicPropMap.get("table.partition.count").isEmpty) {
      topicPropMap.put("table.partition.count", "0")
    }

    if (topicPropMap.get("partition.count").isEmpty) {
      topicPropMap.put("partition.count", "1")
    }

    topicPropMap.toMap
  }
}