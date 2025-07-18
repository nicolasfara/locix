package io.github.locicope.serialization

trait Codec[T] extends Encoder[T], Decoder[T]
