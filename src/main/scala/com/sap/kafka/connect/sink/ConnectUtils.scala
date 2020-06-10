package com.sap.kafka.connect.sink

object ConnectUtils {

  private var configMap: Map[String,String] = null

  def apply(conf:Map[String,String]): Unit = {
    this.configMap = conf
  }

  def setConfigMap(conf: Map[String,String]): Unit = {
    this.configMap = conf
  }

  def getConfigMap: Map[String,String] = this.configMap
}
