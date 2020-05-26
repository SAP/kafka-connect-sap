package com.sap.kafka.connect.source

import java.lang
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import org.apache.kafka.common.utils.Time

class MockTime extends Time {
  private var nanos = System.nanoTime()
  private val autoTickMs = 0

  override def milliseconds(): Long = {
    sleep(autoTickMs)
    TimeUnit.MILLISECONDS.convert(this.nanos, TimeUnit.NANOSECONDS)
  }

  override def nanoseconds(): Long = {
    sleep(autoTickMs)
    nanos
  }

  override def hiResClockMs(): Long = ???

  override def sleep(ms: Long): Unit = {
    this.nanos += TimeUnit.NANOSECONDS.convert(ms, TimeUnit.MILLISECONDS)
  }

  override def waitObject(o: Any, supplier: Supplier[lang.Boolean], l: Long): Unit = ???
}