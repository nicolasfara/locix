package io.github.locicope.serialization

trait Encoder[T]:
  def encode(value: T): Array[Byte]
