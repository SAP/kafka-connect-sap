package com.sap.kafka.utils


import scala.util.{Failure, Try}

/**
 * An opaque object which executes a block of code using the provided
 * input object. In case of any failure, the close() method is executed
 * on the input object.
 */
object WithCloseables  {

  /**
   * The default close method.
   *
   * @param input The object which has been used in the code
   * @tparam I Type of the input object
   */
  def defaultDoClose[I <: AutoCloseable](input: I): Unit =
    Try(input.close()) match {
      case Failure(ex) =>
        // logWarning("Error while closing: {}", ex)
      case _ =>
    }

  /**
   * @param input The object which is used in the code
   * @param block The block of code to execute
   * @tparam I Type of the input object
   * @tparam O Type of the block's execution result
   * @return The block execution result
   */
  def apply[I <: AutoCloseable, O](input: I)(block: (I) => O,
                                             doClose: (I) => Unit = defaultDoClose _): O =
    try {
      block(input)
    } finally {
      doClose(input)
    }

}
