package io.github.locicope.serialization

/**
 * A decoder that can deserialize byte arrays into objects of type T.
 *
 * This trait provides the fundamental operation for converting serialized data
 * back into strongly-typed objects. The decoding operation may fail, which is
 * represented by returning an Either with an error message on the left side.
 *
 * @tparam T the type of object that this decoder can deserialize
 */
trait Decoder[T]:
  /**
   * Decodes a byte array into an object of type T.
   *
   * @param data the byte array containing the serialized data
   * @return Either an error message (Left) if decoding fails, or the decoded object (Right) if successful
   */
  def decode(data: Array[Byte]): Either[String, T]
