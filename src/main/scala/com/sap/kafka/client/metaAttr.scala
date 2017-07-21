package com.sap.kafka.client

import org.apache.kafka.connect.data.Field

// It overrides a JDBC class - keep it starting with lowercase
case class metaAttr(
    name: String,
    dataType: Int,
    isNullable: Int,
    precision: Int,
    scale: Int,
    isSigned: Boolean)

case class MetaSchema(var fields: Seq[metaAttr], var avroFields: Seq[Field])
