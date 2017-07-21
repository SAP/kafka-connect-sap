package com.sap.kafka.client.hana

import java.sql.{Connection, DriverManager, ResultSet}

import com.sap.kafka.client.{ColumnRow, MetaSchema, metaAttr}
import com.sap.kafka.connect.config.hana.HANAConfig
import com.sap.kafka.connect.config.{BaseConfigConstants, BaseParameters}
import com.sap.kafka.utils.hana.HANASchemaBuilder
import com.sap.kafka.utils.{ExecuteWithExceptions, WithCloseables}
import org.apache.kafka.connect.data.{Schema, Struct}
import org.apache.kafka.connect.sink.SinkRecord
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

case class HANAJdbcClient(hanaConfiguration: HANAConfig)  {
  private val log: Logger = LoggerFactory.getLogger(classOf[HANAJdbcClient])

  protected val driver: String = "com.sap.db.jdbc.Driver"

  private val CONNECTION_FAIL_R = ".*Failed to open connection.*".r

  /**
   * Checks whether the provided exception is a connection opening failure one.
   * This method is marked as protected in order to be overrideable in the tests.
   *
   * @param ex The exception to check
   * @return `true` if the exception is a connection failure one, `false` otherwise
   */
  protected def isFailedToOpenConnectionException(ex: Throwable): Boolean = ex match {
    case e if e.getMessage == null => false
    case _: RuntimeException => CONNECTION_FAIL_R.pattern.matcher(ex.getMessage).matches()
    case _ => false
  }

  /**
   * Returns a fully qualified table name.
   *
   * @param namespace The table namespace
   * @param tableName The table name
   * @return the fully qualified table name
   */
  protected def tableWithNamespace(namespace: Option[String], tableName: String) =
    namespace match {
      case Some(value) => s""""$value"."$tableName""""
      case None => tableName
    }

  /**
   * Returns the connection URL. This method is marked as protected in order to be
   * overrideable in the tests.
   *
   * @return The HANA JDBC connection URL
   */
  protected def jdbcUrl: String = {
    hanaConfiguration.connectionUrl
  }

  /**
   * Returns a JDBC connection object. This method is marked as protected in order to be
   * overrideable in the tests.
   *
   * @return The created JDBC [[Connection]] object
   */
   def getConnection: Connection = {
     ExecuteWithExceptions[Connection, HANAConnectorException, HANAJdbcConnectionException] (
      new HANAJdbcConnectionException("Cannot acquire a connection")) { () =>
       val connectionUser: String = hanaConfiguration.connectionUser
       val connectionPassword: String = hanaConfiguration.connectionPassword

       Class.forName(driver)
       Try(DriverManager.getConnection(jdbcUrl, connectionUser, connectionPassword))
       match {
         case Success(conn) => conn
         case Failure(ex) if isFailedToOpenConnectionException(ex) =>
           throw new HANAJdbcConnectionException(s"Opening a connection failed")
         case Failure(ex) =>
           /* Make a distinction between bad state and network failure */
           throw new HANAJdbcBadStateException("Cannot acquire a connection")
       }
     }
  }

  /**
   * Retrieves table metadata.
   *
   * @param tableName The table name
   * @param namespace The table namespace
   * @return A sequence of [[metaAttr]] objects (JDBC representation of the schema)
   */
   def getMetaData(tableName: String, namespace: Option[String]): Seq[metaAttr] = {
     ExecuteWithExceptions[Seq[metaAttr], Exception, HANAJdbcException](
      new HANAJdbcException(s"Fetching of metadata for $tableName failed")) { () =>
       val fullTableName = tableWithNamespace(namespace, tableName)
       WithCloseables(getConnection) { conn =>
         WithCloseables(conn.createStatement()) { stmt =>
           WithCloseables(stmt.executeQuery(s"SELECT * FROM $fullTableName LIMIT 0")) { rs =>
             val metadata = rs.getMetaData
             val columnCount = metadata.getColumnCount
             (1 to columnCount).map(col => metaAttr(
               metadata.getColumnName(col), metadata.getColumnType(col),
               metadata.isNullable(col), metadata.getPrecision(col),
               metadata.getScale(col), metadata.isSigned(col)))
           }
         }
       }
     }
  }

  /**
    * Retrieves query metadata.
    *
    * @param query The SQL Query string
    * @return A sequence of [[metaAttr]] objects (JDBC representation of the schema)
    */
  def getMetadata(query: String): Seq[metaAttr] = {
    ExecuteWithExceptions[Seq[metaAttr], Exception, HANAJdbcException](
      new HANAJdbcException(s"Fetching of metadata for $query failed")) { () =>
        val fullQueryForMetadata = query + " LIMIT 0"
        WithCloseables(getConnection) { conn =>
          WithCloseables(conn.createStatement()) { stmt =>
            WithCloseables(stmt.executeQuery(fullQueryForMetadata)) { rs =>
              val metadata = rs.getMetaData
              val columnCount = metadata.getColumnCount
              (1 to columnCount).map(col => metaAttr(
                metadata.getColumnName(col), metadata.getColumnType(col),
                metadata.isNullable(col), metadata.getPrecision(col),
                metadata.getScale(col), metadata.isSigned(col)))
            }
          }
        }
      }
  }

  /**
   * Creates and loads a table in HANA.
   *
   * @param namespace (optional) The table namespace
   * @param tableName The name of the table.
   * @param tableSchema The schema of the table.
   * @param batchSize Batchsize for inserts
   * @param columnTable Create a columnStore table
   */
  def createTable(namespace: Option[String],
                  tableName: String,
                  tableSchema: MetaSchema,
                  batchSize: Int,
                  columnTable: Boolean = false,
                  keys: List[String] = null,
                  partitionType: String = BaseConfigConstants.NO_PARTITION,
                  partitionCount: Int = 0): Unit = {
    ExecuteWithExceptions[Unit, Exception, HANAJdbcException] (
      new HANAJdbcException(s"Creation of table $tableName failed")) { () =>
      val testSchema = HANASchemaBuilder.avroToHANASchema(tableSchema)
      val fullTableName = tableWithNamespace(namespace, tableName)
      val VARCHAR_STAR_R = "(?i)varchar\\(\\*\\)".r
      // Varchar(*) is not supported by HANA
      val fixedSchema = VARCHAR_STAR_R.replaceAllIn(testSchema, "VARCHAR(1000)")

      var primaryKeys = ""
      if (keys != null) {
        primaryKeys = ", PRIMARY KEY (\"" + keys.mkString("\", \"") + "\")"
      }

      var partition = ""
      if (partitionType == BaseConfigConstants.HASH_PARTITION) {
        if (keys == null) {
          throw new HANAJdbcException("Hash Partition is not supported when keys are" +
            " not specified. Provide the 'pk.mode' parameter")
        } else {
          partition = "PARTITION BY HASH(\"" + keys.mkString("\", \"") + "\") PARTITIONS " + partitionCount
        }
      } else if (partitionType == BaseConfigConstants.ROUND_ROBIN_PARTITION) {
        partition = "PARTITION BY ROUNDROBIN PARTITIONS " + partitionCount
      }

      val query = if (columnTable)
        s"""CREATE COLUMN TABLE $fullTableName ($fixedSchema$primaryKeys) $partition"""
      else
        s"""CREATE TABLE $fullTableName ($fixedSchema$primaryKeys) $partition"""
      log.info(s"Creating table:$tableName with SQL: $query")
      val connection = getConnection
      val stmt = connection.createStatement()
      Try(stmt.execute(query)) match {
        case Failure(ex) => log.error("Error during table creation", ex)
          stmt.close()
          connection.close()
          throw ex
        case _ =>
          stmt.close()
          connection.commit()
          connection.close()
      }
    }
  }

  /**
   * Checks if the given table name corresponds to an existent
   * table in the HANA backend within the provided namespace.
   *
   * @param namespace (optional) The table namespace
   * @param tableName The name of the table
   * @return `true`, if the table exists; `false`, otherwise
   */
  def tableExists(namespace: Option[String], tableName: String): Boolean =
    ExecuteWithExceptions[Boolean, Exception, HANAJdbcException] (
      new HANAJdbcException(s"Checking if table $tableName exists in vora backend failed")) { () =>
      WithCloseables(getConnection) { conn =>
        val dbMetaData = conn.getMetaData
        val tables = dbMetaData.getTables(null, namespace.orNull, tableName, null)
        tables.next()
      }
    }

  /**
   * Returns existing HANA tables which names match the provided pattern and which were created
   * within any schema matching the provided schema pattern.
   *
   * @param dbschemaPattern The schema patters
   * @param tableNamePattern The table name pattern
   * @return [[Success]] with a sequence of the matching tables' names or
   *         [[Failure]] if any error occurred
   */
  def getExistingTables(dbschemaPattern: String, tableNamePattern: String): Try[Seq[String]] =
    ExecuteWithExceptions[Try[Seq[String]], Exception, HANAJdbcException] (
      new HANAJdbcException(s"Retrieving exisiting VORA tables failed matching schema $dbschemaPattern and table $tableNamePattern failed")) { () =>
      WithCloseables(getConnection) { conn =>
        WithCloseables(conn.createStatement()) { stmt =>
          Try {
            val rs = stmt.executeQuery(s"SELECT TABLE_NAME FROM M_TABLES " +
              s"WHERE SCHEMA_NAME LIKE '$dbschemaPattern' AND TABLE_NAME LIKE '$tableNamePattern'")
            val tablesNames = Stream.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
            rs.close()
            tablesNames
          }
        }
      }
    }

  /**
    * Retrieves existing schema information for the given database and table name.
    *
    * @param dbschemaPattern The database schema to filter for.
    * @param tableNamePattern The table name to filter for.
    * @return The [[ColumnRow]]s returned by the query.
    */
  def getExistingSchemas(dbschemaPattern: String, tableNamePattern: String): Seq[ColumnRow] =
    ExecuteWithExceptions[Seq[ColumnRow], Exception, HANAJdbcException] (
      new HANAJdbcException(s"retrieving schema for database $dbschemaPattern and table name $tableNamePattern failed")) { () =>
      WithCloseables(getConnection) { conn =>
        WithCloseables(conn.createStatement()) { stmt =>
          WithCloseables(stmt.executeQuery(
            s"""SELECT SCHEMA_NAME, TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME,
              |LENGTH, SCALE, POSITION, IS_NULLABLE
              |FROM TABLE_COLUMNS
              |WHERE SCHEMA_NAME LIKE '$dbschemaPattern' AND TABLE_NAME LIKE '$tableNamePattern'
           """.stripMargin)) { rs =>
              val iterator = new Iterator[ResultSet] {
              def hasNext: Boolean = rs.next()

                def next(): ResultSet = rs
            }
            // scalastyle:off magic.number of the following numbers.
            iterator.map { rs =>
              ColumnRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(
                  4),
                rs.getInt(5),
                rs.getInt(6),
                rs.getInt(7),
                rs.getBoolean(8))
            }.toList
            // scalastyle:on magic.number
          }
        }
      }
    }

  /**
   * Drops a table from HANA.
   *
   * @param namespace (optional) The table namespace
   * @param tableName The name of the table to drop
   */
  def dropTable(namespace: Option[String], tableName: String): Unit =
    ExecuteWithExceptions[Unit, Exception, HANAJdbcException] (
      new HANAJdbcException(s"drop table $tableName is not successful")) { () =>
      WithCloseables(getConnection) { conn =>
        WithCloseables(conn.createStatement()) { stmt =>
          val fullTableName = tableWithNamespace(namespace, tableName)
          Try(stmt.execute(s"""DROP TABLE $fullTableName""")) match {
            case Failure(ex) =>
              throw ex
            case _ =>
          }
        }
      }
    }

  /**
   * Loads data to a table from a given Seq of SinkRecords.
   *
   * @param namespace (optional) The table namespace
   * @param tableName The table name to which the SinkRecords are loaded
   * @param connection The HANA JDBC Client Connection
   * @param records The SinkRecords to load
   * @param batchSize The batch size parameter
   */
   def loadData(namespace: Option[String],
                             tableName: String,
                            connection: Connection,
                             schema: MetaSchema,
                             records: Seq[SinkRecord],
                             batchSize: Int): Unit = {
     ExecuteWithExceptions[Unit, Exception, HANAJdbcException] (
      new HANAJdbcException(s"loading data into $tableName is not successful")) { () =>
       val fullTableName = tableWithNamespace(namespace, tableName)
       HANAPartitionLoader.loadPartition(connection, fullTableName, records.iterator, schema, batchSize)
     }
  }

  /**
    *
    * @param tableNameOrSubquery table name or subquery in HANA
    */
  def getQueryStatistics(tableNameOrSubquery: String): Int = {
    ExecuteWithExceptions[Int, Exception, HANAJdbcException] (
      new HANAJdbcException(s"cannot fetch query statistics for table or subquery $tableNameOrSubquery")) { () =>
      WithCloseables(getConnection) { conn =>
        WithCloseables(conn.createStatement()) { stmt =>
          WithCloseables(stmt.executeQuery(s"select count(*) as NUM_ROWS from $tableNameOrSubquery")) {
            resultSet =>
              resultSet.next()
              resultSet.getInt("NUM_ROWS")
          }
        }
      }
    }
  }

  /**
    * Executes a SQL query on HDB tables.
    * @param schema Kafka schema for SourceRecord
    * @param queryString query string to be executed on HANA
    * @param offset offset for the query string
    * @param limit limit for the query string
    * @param isColumnar if the table is columnar
    */
  def executeQuery(schema: Schema, queryString: String, offset: Int, limit: Int,
                   isColumnar: Boolean = false): Option[List[Struct]] = {
    ExecuteWithExceptions[Option[List[Struct]], Exception, HANAJdbcException] (
      new HANAJdbcException(s"execution of query $queryString failed")) { () =>
      WithCloseables(getConnection) { conn =>
        WithCloseables(conn.createStatement()) { stmt =>
          if (limit > 0 && offset > 0) {
            WithCloseables(stmt.executeQuery(s"$queryString LIMIT $limit OFFSET $offset")) { resultSet =>
              fetchResultSet(resultSet, schema)
            }
          } else if (limit > 0) {
            WithCloseables(stmt.executeQuery(s"$queryString LIMIT $limit")) { resultSet =>
              fetchResultSet(resultSet, schema)
            }
          } else {
            WithCloseables(stmt.executeQuery(s"$queryString")) { resultSet =>
              fetchResultSet(resultSet, schema)
            }
          }
        }
      }
    }
  }

  def commit(connection: Connection): Unit = {
    ExecuteWithExceptions[Unit, Exception, HANAJdbcConnectionException] (
      new HANAJdbcConnectionException("commit failed for current connection")) { () =>
        connection.commit()
    }
  }

  private def fetchResultSet(resultSet: ResultSet, schema: Schema): Option[List[Struct]] = {
    var dm = List[Struct]()

    while (resultSet.next()) {
      val metadata = resultSet.getMetaData
      val struct = new Struct(schema)

      for (i <- 1 to metadata.getColumnCount) {
        val dataType = metadata.getColumnType(i)
        val columnValue = getColumnData(dataType, resultSet, i)
        struct.put(metadata.getColumnName(i), columnValue)
      }
      dm :+= struct
    }
    Some(dm)
  }

  private def getColumnData(columnType: Int, resultSet: ResultSet, index: Int) = {
    val value = columnType match {
      case java.sql.Types.VARCHAR | java.sql.Types.NVARCHAR |
        java.sql.Types.NCHAR | java.sql.Types.CHAR | java.sql.Types.LONGNVARCHAR
        | java.sql.Types.LONGNVARCHAR => resultSet.getString(index)

      case java.sql.Types.BOOLEAN => resultSet.getBoolean(index)

      case java.sql.Types.BINARY | java.sql.Types.VARBINARY |
        java.sql.Types.LONGVARBINARY | java.sql.Types.BLOB => resultSet.getBytes(index)

      case java.sql.Types.TINYINT | java.sql.Types.INTEGER |
        java.sql.Types.SMALLINT => resultSet.getInt(index)

      case java.sql.Types.BIGINT => resultSet.getLong(index)

      case java.sql.Types.FLOAT | java.sql.Types.REAL => resultSet.getFloat(index)

      case java.sql.Types.DECIMAL => resultSet.getString(index) match {
        case null => null
        case value => new java.math.BigDecimal(value)
      }

      case java.sql.Types.DOUBLE => resultSet.getDouble(index)

      case java.sql.Types.DATE => resultSet.getDate(index)
      case java.sql.Types.TIME => resultSet.getTime(index)
      case java.sql.Types.TIMESTAMP => resultSet.getTimestamp(index)

      case java.sql.Types.NCLOB | java.sql.Types.CLOB => resultSet.getString(index)

      case _ => throw new UnsupportedOperationException(s"HANA Jdbc Client doesn't " +
        s"support column types with typecode '$columnType'")
    }

    if (resultSet.wasNull()) {
      null
    } else {
      value
    }
  }

}
