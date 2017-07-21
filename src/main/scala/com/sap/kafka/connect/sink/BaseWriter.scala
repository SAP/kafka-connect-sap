package com.sap.kafka.connect.sink

import java.sql.Connection
import java.util

import org.apache.kafka.connect.sink.SinkRecord
import org.slf4j.{Logger, LoggerFactory}


abstract class BaseWriter {

 private val log: Logger = LoggerFactory.getLogger(getClass)
 private var connection:Connection = null

  protected[sink] def initializeConnection(): Unit

  protected[sink] def write(records: util.Collection[SinkRecord]): Unit


 private[sink] def close(): Unit = {
   if (connection != null) {
     try {
       connection.close()
       connection = null
     }
     catch {
       case _: Exception => log.warn("Ignoring error closing connection")
     }
   }
 }
}
