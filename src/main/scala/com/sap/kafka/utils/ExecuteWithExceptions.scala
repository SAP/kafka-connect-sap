package com.sap.kafka.utils

import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

/**
  * An opaque object which executes a block of code in a try block, catches the technical
  * exception & throws a connector exception . For converting the exceptions, the doCatch()
  * method is executed.
  */
object ExecuteWithExceptions {
  private val log = LoggerFactory.getLogger(getClass)

  /**
    * The default doCatch method.
    *
    * @param exception The connector exception object which has to be thrown
    * @tparam TE Technical Exception type
    * @tparam BE Connector Exception type
    */
  def defaultThrowException[TE <: Exception, BE <: ConnectorException](exception: TE,
                                                                       connectorException: BE): BE = {
    log.error(connectorException.getMessage)
    connectorException.initCause(exception)
    connectorException
  }

  /**
    * @param connectorException The connector exception object which has to be thrown
    * @param block The block of code to execute
    * @tparam O Type of the block's execution result
    * @tparam TE Technical Exception type
    * @tparam BE Connector Exception type
    * @return The block execution result
    */
  def apply[O, TE <: Exception: ClassTag, BE <: ConnectorException](connectorException: BE)(block: () => O,
                                                                                            doCatch: (TE, BE) => BE = defaultThrowException[TE, BE] _): O = {
    try {
      block()
    } catch {
      case exception: TE =>
        throw doCatch(exception, connectorException)
    }
  }
}