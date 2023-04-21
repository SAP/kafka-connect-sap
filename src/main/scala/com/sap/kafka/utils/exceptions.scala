package com.sap.kafka.utils

class SchemaNotMatchedException(msg: String) extends org.apache.kafka.connect.errors.ConnectException(msg)