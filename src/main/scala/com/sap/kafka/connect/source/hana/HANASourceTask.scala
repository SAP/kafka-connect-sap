package com.sap.kafka.connect.source.hana

import com.sap.kafka.client.hana.{HANAConfigInvalidInputException, HANAJdbcClient}
import com.sap.kafka.connect.config.BaseConfigConstants
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.connect.source.GenericSourceTask
import org.apache.kafka.common.utils.Time

class HANASourceTask extends GenericSourceTask {

  def this(time: Time, jdbcClient: HANAJdbcClient) = {
    this()
    this.time = time
    this.jdbcClient = jdbcClient
  }

  override def version(): String = {
    getClass.getPackage.getImplementationVersion
  }

  override def createJdbcClient(): HANAJdbcClient = {
    config match {
      case hanaConfig: HANAConfig => new HANAJdbcClient(hanaConfig)
      case _ => throw new RuntimeException("Cannot create HANA Jdbc Client")
    }
  }

  override def getTables(tables: List[Tuple2[String, String]])
  : List[Tuple4[String, Int, String, String]] = {
    val connection = jdbcClient.getConnection

    // contains fullTableName, partitionNum, fullTableName + partitionNum, topicName
    var tableInfos: List[Tuple4[String, Int, String, String]] = List()
    val noOfTables = tables.size
    var tablecount = 1

    var stmtToFetchPartitions = s"SELECT SCHEMA_NAME, TABLE_NAME, PARTITION FROM SYS.M_CS_PARTITIONS WHERE "
    tables.foreach(table => {
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
    })

    val stmt = connection.createStatement()
    val partitionRs = stmt.executeQuery(stmtToFetchPartitions)

    while(partitionRs.next()) {
      val tableName = "\"" + partitionRs.getString(1) + "\".\"" + partitionRs.getString(2) + "\""
      tableInfos :+= Tuple4(tableName, partitionRs.getInt(3), tableName + partitionRs.getInt(3),
        tables.filter(table => table._1 == tableName)
          .map(table => table._2).head.toString)
    }

    // fill tableInfo for tables whose entry is not in M_CS_PARTITIONS
    val tablesInInfo = tableInfos.map(tableInfo => tableInfo._1)
    val tablesToBeAdded = tables.filterNot(table => tablesInInfo.contains(table._1))

    tablesToBeAdded.foreach(tableToBeAdded => {
      tableInfos :+= Tuple4(tableToBeAdded._1, 0, tableToBeAdded._1 + "0", tableToBeAdded._2)
    })

    tableInfos
  }

  override protected def getQueries(queryTuple: List[(String, String)]): List[(String, Int, String, String)] =
    queryTuple.map(query => (query._1, 0, null, query._2))
}