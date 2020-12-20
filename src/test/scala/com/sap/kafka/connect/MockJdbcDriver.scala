package com.sap.kafka.connect

import java.sql.{Connection, Driver, DriverPropertyInfo}
import java.util.Properties
import java.util.logging.Logger

class MockJdbcDriver(delegate: Driver) extends Driver {
  override def connect(url: String, info: Properties): Connection = {
    return delegate.connect(url, info)
  }

  override def acceptsURL(url: String): Boolean = {
    return delegate.acceptsURL(url)
  }

  override def getPropertyInfo(url: String, info: Properties): Array[DriverPropertyInfo] = {
    return delegate.getPropertyInfo(url, info)
  }

  override def getMajorVersion: Int = {
    delegate.getMajorVersion
  }

  override def getMinorVersion: Int = {
    delegate.getMinorVersion
  }

  override def jdbcCompliant(): Boolean = {
    return delegate.jdbcCompliant
  }

  override def getParentLogger: Logger = {
    return delegate.getParentLogger
  }
}
