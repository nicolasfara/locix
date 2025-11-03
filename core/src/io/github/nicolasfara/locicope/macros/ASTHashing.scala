package io.github.nicolasfara.locicope.macros

import java.nio.charset.StandardCharsets

import scala.quoted.*

object ASTHashing:
  inline def hashBody(inline body: Any): String = ${ astHashImpl('body) }

  private def astHashImpl(body: Expr[Any])(using Quotes): Expr[String] =
    import quotes.reflect.*
    val pos = Position.ofMacroExpansion
    val hashed = fletcher16Checksum(
      s"${pos.sourceFile.name}:${pos.start}:${pos.end}:${body.show}",
    ).toHexString
    Expr(hashed)

  def fletcher16Checksum(input: String): Int =
    val bytes = input.getBytes(StandardCharsets.UTF_8)
    var sum1 = 0
    var sum2 = 0

    for byte <- bytes do
      sum1 = (sum1 + (byte & 0xff)) % 255
      sum2 = (sum2 + sum1) % 255

    (sum2 << 8) | sum1
end ASTHashing
