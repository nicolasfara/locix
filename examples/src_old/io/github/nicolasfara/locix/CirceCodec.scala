package io.github.nicolasfara.locix

import io.circe.parser.decode as circeDecode
import io.circe.syntax.*
import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }

import serialization.Codec

object CirceCodec:
  export io.circe.generic.auto.*

  given foo[T: {CirceEncoder, CirceDecoder}]: Codec[T] with
    override def decode(data: Array[Byte]): Either[String, T] = circeDecode(data.map(_.toChar).mkString).left.map(_.getMessage)
    override def encode(value: T): Array[Byte] = value.asJson.noSpaces.getBytes
