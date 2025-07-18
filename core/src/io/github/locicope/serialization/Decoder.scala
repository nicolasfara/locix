package io.github.locicope.serialization

trait Decoder[T]:
  def decode(data: Array[Byte]): Either[String, T]
