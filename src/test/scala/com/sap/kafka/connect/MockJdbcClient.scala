package com.sap.kafka.connect

import com.sap.kafka.client.hana.HANAJdbcClient
import com.sap.kafka.connect.config.BaseConfig
import com.sap.kafka.connect.config.hana.HANAConfig

class MockJdbcClient(configuration: BaseConfig)
  extends HANAJdbcClient(configuration.asInstanceOf[HANAConfig]) {
  override val driver: String = "org.h2.Driver"
}