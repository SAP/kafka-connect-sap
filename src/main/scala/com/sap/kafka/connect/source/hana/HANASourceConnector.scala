package com.sap.kafka.connect.source.hana

import com.sap.kafka.client.hana.{HANAConfigInvalidInputException, HANAConfigMissingException, HANAJdbcClient}
import com.sap.kafka.connect.config.BaseConfigConstants
import com.sap.kafka.connect.config.hana.{HANAConfig, HANAParameters}
import com.sap.kafka.utils.ExecuteWithExceptions

import java.util
import org.apache.kafka.common.config.{ConfigDef, ConfigException}
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.{SourceConnector, SourceConnectorContext}

import scala.collection.JavaConverters._

class HANASourceConnector extends SourceConnector {
  private var configRawProperties: Option[util.Map[String, String]] = None
  private var hanaClient: HANAJdbcClient = _
  private var tableOrQueryInfos: List[Tuple3[String, Int, String]] = _
  private var configProperties: HANAConfig = _
  override def context(): SourceConnectorContext = super.context()
  override def version(): String = getClass.getPackage.getImplementationVersion

  override def start(properties: util.Map[String, String]): Unit = {
    configRawProperties = Some(properties)
    configProperties = HANAParameters.getConfig(properties)
    hanaClient = new HANAJdbcClient(configProperties)

    val topics = configProperties.topics
    var tables: List[(String, String)] = Nil
    if (topics.forall(topic => configProperties.topicProperties(topic).keySet.contains("table.name"))) {
      tables = topics.map(topic =>
        (configProperties.topicProperties(topic)("table.name"), topic))
    }
    var query: List[(String, String)] = Nil
    if (topics.forall(topic => configProperties.topicProperties(topic).keySet.contains("query"))) {
      query = topics.map(topic =>
        (configProperties.topicProperties(topic)("query"), topic))
    }

    if (tables.isEmpty && query.isEmpty) {
      throw new ConnectException("Invalid configuration: each HANAConnector must have one table or query associated")
    }

    tableOrQueryInfos = configProperties.queryMode match {
      case BaseConfigConstants.QUERY_MODE_TABLE =>
        getTables(hanaClient, tables)
      case BaseConfigConstants.QUERY_MODE_SQL =>
        getQueries(query)
    }
  }

  override def taskClass(): Class[_ <: Task] = classOf[HANASourceTask]

  override def taskConfigs(maxTasks: Int): util.List[util.Map[String, String]] = {
    val tableOrQueryGroups = createTableOrQueryGroups(tableOrQueryInfos, maxTasks)
    createTaskConfigs(tableOrQueryGroups, configRawProperties.get).asJava
  }

  override def stop(): Unit = {

  }

  override def config(): ConfigDef = {
    new ConfigDef
  }

  private def getTables(hanaClient: HANAJdbcClient, tables: List[Tuple2[String, String]]) : List[Tuple3[String, Int, String]] = {
    val connection = hanaClient.getConnection

    // contains fullTableName, partitionNum, topicName
    var tableInfos: List[Tuple3[String, Int, String]] = List()
    val noOfTables = tables.size
    var tablecount = 1

    var stmtToFetchPartitions = s"SELECT SCHEMA_NAME, TABLE_NAME, PART_ID FROM SYS.M_CS_TABLES WHERE "

    tables.foreach(table => {
      if (!(configProperties.topicProperties(table._2)("table.type") == BaseConfigConstants.COLLECTION_TABLE_TYPE)) {
        table._1 match {
          case BaseConfigConstants.TABLE_NAME_FORMAT(schema, tablename) =>
            stmtToFetchPartitions += s"(SCHEMA_NAME = '$schema' AND TABLE_NAME = '$tablename')"

            if (tablecount < noOfTables) {
              stmtToFetchPartitions += " OR "
            }
            tablecount = tablecount + 1
          case _ =>
            throw new HANAConfigInvalidInputException("The table name is invalid. Does not follow naming conventions")
        }
      }
    })

    if (tablecount > 1) {
      val stmt = connection.createStatement()
      val partitionRs = stmt.executeQuery(stmtToFetchPartitions)

      while (partitionRs.next()) {
        val tableName = "\"" + partitionRs.getString(1) + "\".\"" + partitionRs.getString(2) + "\""
        tableInfos :+= Tuple3(tableName, partitionRs.getInt(3),
          tables.filter(table => table._1 == tableName).map(table => table._2).head.toString)
      }
    }

    // fill tableInfo for tables whose entry is not in M_CS_TABLES
    val tablesInInfo = tableInfos.map(tableInfo => tableInfo._1)
    val tablesToBeAdded = tables.filterNot(table => tablesInInfo.contains(table._1))

    tablesToBeAdded.foreach(tableToBeAdded => {
      if (configProperties.topicProperties(tableToBeAdded._2)("table.type") == BaseConfigConstants.COLLECTION_TABLE_TYPE) {
        tableInfos :+= Tuple3(getTableName(tableToBeAdded._1)._2, 0, tableToBeAdded._2)
      } else {
        tableInfos :+= Tuple3(tableToBeAdded._1, 0, tableToBeAdded._2)
      }
    })

    tableInfos
  }

  private def getQueries(queryTuple: List[(String, String)]): List[(String, Int, String)] =
    queryTuple.map(query => (query._1, 0, query._2))

  private def createTableOrQueryGroups(tableOrQueryInfos: List[Tuple3[String, Int, String]], count: Int)
  : List[List[Tuple3[String, Int, String]]] = {
    val groupSize = count match {
      case c if c > tableOrQueryInfos.size => 1
      case _ => ((tableOrQueryInfos.size + count - 1) / count)
    }
    tableOrQueryInfos.grouped(groupSize).toList
  }

  private def createTaskConfigs(tableOrQueryGroups: List[List[Tuple3[String, Int, String]]], config: java.util.Map[String, String])
  : List[java.util.Map[String, String]] = {
    tableOrQueryGroups.map(g => {
      var gconfig = new java.util.HashMap[String,String](config)
      for ((t, i) <- g.zipWithIndex) {
        gconfig.put(s"_tqinfos.$i.name", t._1)
        gconfig.put(s"_tqinfos.$i.partition", t._2.toString)
        gconfig.put(s"_tqinfos.$i.topic", t._3)
      }
      gconfig
    })
  }

  private def getTableName(tableName: String): (Option[String], String) = {
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
}