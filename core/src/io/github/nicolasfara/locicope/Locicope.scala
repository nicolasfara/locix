package io.github.nicolasfara.locicope

case class Locicope[F](effect: F)

object Locicope:
  trait Handler[F, A, B]:
    def handle(program: Locicope[F] ?=> A): B

  def handle[F, A, B](program: Locicope[F] ?=> A)(using handler: Handler[F, A, B]): B =
    handler.handle(program)
