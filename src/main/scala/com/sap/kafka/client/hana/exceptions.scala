package com.sap.kafka.client.hana

import com.sap.kafka.utils.ConnectorException

class HANAConnectorException(msg: String) extends ConnectorException(msg)

class HANAConfigInvalidInputException(msg: String) extends HANAConnectorException(msg)

class HANAConfigMissingException(msg: String) extends HANAConnectorException(msg)

class HANAJdbcException(msg: String) extends HANAConnectorException(msg)

class HANAJdbcConnectionException(msg: String) extends HANAConnectorException(msg)

class HANAJdbcBadStateException(msg: String) extends HANAJdbcException(msg)
