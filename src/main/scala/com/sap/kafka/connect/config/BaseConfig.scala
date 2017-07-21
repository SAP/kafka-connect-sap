package com.sap.kafka.connect.config


abstract class BaseConfig(props: Map[String, String]) {


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
  def maxRetries = props.getOrElse[String]("max.retries", "1").toInt

  /**
    * Auto create HANA tables if table is not found for Sink.
    * Default is false.
    */
  def autoCreate = props.getOrElse[String]("auto.create", "false").toBoolean

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
        if (value == BaseConfigConstants.ROUND_ROBIN_PARTITION)
          topicPropMap.put("table.partition.mode", value)
        else if (value == BaseConfigConstants.HASH_PARTITION)
          topicPropMap.put("table.partition.mode", value)
      }

      /**
        * table partition count to be used by sink
        * Default value is 0.
        */
      if (key == s"$topic.table.partition.count") {
        topicPropMap.put("table.partition.count", value)
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