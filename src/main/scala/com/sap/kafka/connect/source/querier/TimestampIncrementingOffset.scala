package com.sap.kafka.connect.source.querier

import java.util
import com.sap.kafka.client.hana.HANAConfigMissingException
import com.sap.kafka.utils.ExecuteWithExceptions

import java.text.SimpleDateFormat

class TimestampIncrementingOffset(incrementingOffset: String) {
  def getIncrementingOffset(): String = {
    incrementingOffset
  }

  def toMap(): util.Map[String, Object] = {
    val map = new util.HashMap[String, Object]()

    if (incrementingOffset != TimestampIncrementingOffset.DEFAULT_VAL &&
      incrementingOffset != TimestampIncrementingOffset.DEFAULT_DATE) {
      map.put(TimestampIncrementingOffset.INCREMENTING_FIELD,
        incrementingOffset.toString)
    }
    map
  }
}

object TimestampIncrementingOffset {
  var INCREMENTING_FIELD: String = _

  import java.text.SimpleDateFormat
  import java.util.TimeZone

  val UTC_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  UTC_DATETIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"))

  private val DEFAULT_DATE = "1970-01-01 00:00:00.000"
  private val DEFAULT_VAL = "-1"
  private val STRING_VALIDATOR = "^[\\w :-]+$".r

  def apply(map: Map[String, _], incrColumn: String,
            incrColType: Int): TimestampIncrementingOffset = {
    INCREMENTING_FIELD = incrColumn
    if (map == null || map.isEmpty) {
      if (incrColType == java.sql.Types.DATE ||
        incrColType == java.sql.Types.TIME ||
        incrColType == java.sql.Types.TIMESTAMP) {
        return new TimestampIncrementingOffset(DEFAULT_DATE)
      }
      return new TimestampIncrementingOffset(DEFAULT_VAL)
    }

    ExecuteWithExceptions[TimestampIncrementingOffset, Exception, HANAConfigMissingException] (
      new HANAConfigMissingException("incrementing field not found. " +
        "Clear previously set offsets or check source configurations")) { () =>
      val incr = map(INCREMENTING_FIELD).toString
      new TimestampIncrementingOffset(incr)
    }
  }

  def convertToTableDataType(value: String, dataType: Int) = {
    dataType match {
      case java.sql.Types.DATE | java.sql.Types.TIME | java.sql.Types.TIMESTAMP =>
        s"'$value'"
      case java.sql.Types.VARCHAR | java.sql.Types.NVARCHAR | java.sql.Types.CHAR | java.sql.Types.NCHAR =>
        // REVISIT this is a temporary workaround while using a plain statement
        val sanitized = value match {
          case STRING_VALIDATOR() => value
          case _ => value.replaceAll("[^\\w :-]", "")
        }
        s"'$sanitized'"
      case _ =>
        value
    }
  }
}