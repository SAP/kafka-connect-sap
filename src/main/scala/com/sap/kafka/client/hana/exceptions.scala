package com.sap.kafka.client.hana

import org.apache.kafka.connect.errors.{ConnectException, RetriableException}

class HANAConnectorException(msg: String) extends ConnectException(msg)

class HANAConnectorRetriableException(msg: String) extends RetriableException(msg)

class HANAConfigInvalidInputException(msg: String) extends HANAConnectorException(msg)

class HANAConfigMissingException(msg: String) extends HANAConnectorException(msg)

class HANAJdbcException(msg: String) extends HANAConnectorRetriableException(msg)

class HANAJdbcConnectionException(msg: String) extends HANAConnectorRetriableException(msg)

class HANAJdbcBadStateException(msg: String) extends HANAConnectorRetriableException(msg)
