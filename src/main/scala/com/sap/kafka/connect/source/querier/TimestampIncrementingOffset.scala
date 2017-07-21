package com.sap.kafka.connect.source.querier

import java.util

import com.sap.kafka.client.hana.HANAConfigMissingException
import com.sap.kafka.utils.ExecuteWithExceptions

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

  private val DEFAULT_DATE = "1970-01-01 00:00:00.000"
  private val DEFAULT_VAL = "-1"

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
    if (dataType == java.sql.Types.DATE) {
      s"'$value'"
    } else if (dataType == java.sql.Types.TIME) {
      s"'$value'"
    } else if (dataType == java.sql.Types.TIMESTAMP) {
      s"'$value'"
    } else {
      value
    }
  }
}