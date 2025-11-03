package io.github.nicolasfara.locicope

import io.circe.syntax.*
import io.circe.generic.auto.*
import io.circe.parser.decode as circeDecode
import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }
import io.circe.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.serialization.Codec

object CirceCodec:
  given foo[T: {CirceEncoder, CirceDecoder}]: Codec[T] with
    override def decode(data: Array[Byte]): Either[String, T] = circeDecode(data.map(_.toChar).mkString).left.map(_.getMessage)
    override def encode(value: T): Array[Byte] = value.asJson.noSpaces.getBytes
