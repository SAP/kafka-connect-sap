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

  override def version(): String = getClass.getPackage.getImplementationVersion

  override def createJdbcClient(): HANAJdbcClient = {
    config match {
      case hanaConfig: HANAConfig => new HANAJdbcClient(hanaConfig)
      case _ => throw new RuntimeException("Cannot create HANA Jdbc Client")
    }
  }

}
