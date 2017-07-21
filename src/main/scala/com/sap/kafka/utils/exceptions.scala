package com.sap.kafka.utils

class ConnectorException(msg: String) extends Exception(msg)

class SchemaNotMatchedException(msg: String) extends ConnectorException(msg)