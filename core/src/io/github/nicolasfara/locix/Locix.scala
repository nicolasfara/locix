package io.github.nicolasfara.locix

case class Locix[+F](effect: F)

object Locix:
  trait Handler[+F, -A, +B]:
    def handle(program: Locix[F] ?=> A): B

  inline def handle[F, A, B](program: Locix[F] ?=> A)(using handler: Handler[F, A, B]): B =
    handler.handle(program)
