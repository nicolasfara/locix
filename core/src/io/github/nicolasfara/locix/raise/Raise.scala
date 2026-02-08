package io.github.nicolasfara.locix.raise

import scala.caps.Control
import scala.util.control.NonFatal
import scala.util.boundary
import scala.util.boundary.break

trait Raise[E] extends Control:
  def raise(e: -> E): Nothing

object Raise:
  def raise[E](using r: Raise[E])(e: -> E): Nothing = r.raise(e)

  def fold[E, A, B](block: Raise[E] ?=> A)(onError: E -> B)(onSuccess: A -> B): B = boundary:
    given Raise[E] with
      def raise(e: -> E): Nothing = break(onError(e))
    val result = block
    onSuccess(result)

  def ensure[E](using r: Raise[E])(condition: Boolean)(e: -> E): Unit =
    if !condition then r.raise(e)

  def catchNonFatal[E, A](using r: Raise[E])(block: => A)(handler: Throwable -> E): A =
    try block
    catch 
      case NonFatal(t) => raise(handler(t))
      case ex => throw ex

  extension [E, V](option: Option[V])(using r: Raise[E])
    def value(e: -> E): V = option match
      case Some(value) => value
      case None => raise(e)
