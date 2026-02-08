package io.github.nicolasfara.locix

import language.experimental.separationChecking
import scala.caps.Mutable

import scala.caps.Capability

case class Lx[+F](effect: F) extends Mutable

object Lx:
  trait Handler[+F, -A, +B]:
    def handle(program: Lx[F] ?=> A): B

  inline def handle[F, A, B](program: Lx[F] ?=> A)(using handler: Handler[F, A, B]): B =
    handler.handle(program)

object Test:
  type Test = Lx[Test.Effect]

  def foo[V](using t: Test^)(body: () => V): V = t.effect.foo(body)

  def run(program: Test ?=> Unit): Unit = 
    val handler = new Lx.Handler[Test.Effect, Unit, Unit]:
      override def handle(program: Lx[Effect] ?=> Unit): Unit = program(using Lx(effectImpl))
    Lx.handle(program)(using handler)

  private val effectImpl: Effect = new Effect:
    def foo[V](body: () => V): V = body()

  trait Effect:
    def foo[V](body: () => V): V

object Test2:
  import Test.*
  def b(using Test^): String = foo(() => "Hello")
  def f(using Test^): Unit =
    foo { () =>
      // foo { () =>
      //   println("Hello")
      // }
      // b
    }

    foo(() => "Hello")

  // class Matrix(nrows: Int, ncols: Int) extends Mutable:
  //   update def setElem(i: Int, j: Int, x: Double): Unit = ???
  //   def getElem(i: Int, j: Int): Double = ???

  // def multiply(a: Matrix, b: Matrix, c: Matrix^): Unit =
  //   ???

  // locally:
  //   val a = new Matrix(10, 10)
  //   val b = new Matrix(10, 10)
  //   val c = new Matrix(10, 10)
  //   multiply(a, b, c)


  // class PlacedValue extends caps.Mutable:
  //   update def dummy(): Unit = ()

  // def foo[V](using pv: PlacedValue^)(f: () => V): V^{pv} =
  //   ???

  // def baz(using pv: PlacedValue^) = foo { () =>
  //   "fdfd"
  // }

  // def bar =
  //   given (PlacedValue^) = new PlacedValue
  //   foo { () =>

  //     baz

  //   }
