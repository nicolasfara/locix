package io.github.nicolasfara.locicope.serialization

/**
 * A codec that combines encoding and decoding functionality for type T.
 *
 * This trait extends both [[Encoder]] and [[Decoder]], providing a unified interface for bidirectional serialization of
 * objects of type T.
 *
 * @tparam T
 *   the type of object that this codec can encode and decode
 */
trait Codec[T] extends Encoder[T], Decoder[T]
