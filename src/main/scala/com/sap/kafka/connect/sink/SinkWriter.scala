package com.sap.kafka.connect.sink

import com.sap.kafka.connect.config.BaseConfig


trait SinkWriter {

  def initWriter(config: BaseConfig): BaseWriter

}
