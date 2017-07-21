package com.sap.kafka.connect.sink

import java.util

import com.sap.kafka.connect.config.BaseConfig
import com.sap.kafka.utils.ConnectorException
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.connect.sink.{SinkRecord, SinkTask}
import org.slf4j.Logger


abstract class GenericSinkTask extends SinkTask with SinkWriter   {
    /**
     * Parse the configurations and setup the writer
     * */
    var log: Logger = _
    var config:BaseConfig = null
    var writer:BaseWriter = null
    private var retriesLeft:Int = 0

    /**
     * Pass the SinkRecords to the writer for Writing
     * */
    override def put(records: util.Collection[SinkRecord]): Unit = {
      log.info(s"PHASE - 1 - get records from kafka, Started for task with assigned " +
        s"partitions ${this.context.assignment().toString} ")
      log.info(s"Number of Records read for Sink: ${records.size}")
      retriesLeft = config.maxRetries
      if (records.isEmpty) {
        return
      }
      val recordsCount: Int = records.size
      log.trace("Received {} records for Sink", recordsCount)
      try {
        writer.write(records)
      } catch  {
        case exception : ConnectorException =>
          log.error("Write of {} records failed, remainingRetries={}", records.size(), retriesLeft)
          while (retriesLeft > 0) {
            try {
              retriesLeft = retriesLeft - 1
              writer.close()
              writer = initWriter(config)
              writer.write(records)
              retriesLeft = -1
            } catch {
              case exception: ConnectorException =>
                // ignore
            }
          }

          if (retriesLeft == 0)
            throw exception
      } finally {
        log.info(s"PHASE - 1 ended for task, with assigned partitions ${this.context.assignment().toString}")
      }
    }


    override def stop(): Unit = {
      log.info("Stopping task")
      writer.close()
    }

    override def flush(map: util.Map[TopicPartition, OffsetAndMetadata]) : Unit = {

    }

    override def version(): String = getClass.getPackage.getImplementationVersion


}
