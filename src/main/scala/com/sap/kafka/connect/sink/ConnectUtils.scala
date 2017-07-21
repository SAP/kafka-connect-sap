package com.sap.kafka.connect.sink

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient


object ConnectUtils {

  private var configMap: Map[String,String] = null
  private var schemaRegistryClient:CachedSchemaRegistryClient = _

  def apply(conf:Map[String,String]): Unit = {
    this.configMap = conf
    schemaRegistryClient = new CachedSchemaRegistryClient(conf.get("schema.registry.url").get, 1)

  }

  def setConfigMap(conf: Map[String,String]): Unit = {
    this.configMap = conf
  }

  def getConfigMap: Map[String,String] = this.configMap

  def getSchemaRegistryClient: CachedSchemaRegistryClient = schemaRegistryClient


}
