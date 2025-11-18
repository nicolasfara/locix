package io.github.nicolasfara.locix.serialization

/**
 * An encoder that can serialize objects of type T into byte arrays.
 *
 * This trait provides the fundamental operation for converting strongly-typed objects into their serialized byte representation. Implementations
 * should handle the conversion logic specific to the type T.
 *
 * @tparam T
 *   the type of object that this encoder can serialize
 */
trait Encoder[T]:
  /**
   * Encodes an object of type T into a byte array.
   *
   * @param value
   *   the object to be serialized
   * @return
   *   the byte array representation of the encoded object
   */
  def encode(value: T): Array[Byte]
