package com.sap.kafka.client.hana

import com.sap.kafka.connect.config.hana.HANAConfig

/**
 * The [[AbstractHANAPartitionLoader]] which uses [[HANAJdbcClient]].
 */
object HANAPartitionLoader extends AbstractHANAPartitionLoader {

  /** @inheritdoc */
  def getHANAJdbcClient(hanaConfiguration: HANAConfig): HANAJdbcClient =
    new HANAJdbcClient(hanaConfiguration)

}
