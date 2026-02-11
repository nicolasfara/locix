package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.network.Identifier
import scala.concurrent.ExecutionContext
import scala.collection.mutable
import scala.concurrent.Future
import java.util.concurrent.CountDownLatch
import scala.concurrent.Await

trait Emitter[V]:
  def emit(value: V): Unit
  def close(): Unit

trait Subscription:
  def cancel(): Unit

trait Signal[V]:
  def subscribe(callback: V -> Unit): Subscription
  def onClose(callback: () -> Unit): Unit
  def map[U](f: V -> U): Signal[U]

object Signal:
  def make[V](using ec: ExecutionContext): (Signal[V], Emitter[V]) =
    val signal = new SignallingImpl[V]
    (signal, signal)

  private class SignallingImpl[V](using ExecutionContext) extends Signal[V], Emitter[V]:
    private var subscribers = mutable.Map.empty[Long, V -> Unit]
    private var onCloseCallbacks = List.empty[() -> Unit]
    private var counter: Long = 0L
    private var closed: Boolean = false

    override def onClose(callback: () -> Unit): Unit = synchronized:
      if (closed) callback()
      else onCloseCallbacks = callback :: onCloseCallbacks

    override def map[U](f: V -> U): Signal[U] =
      val mappedSignal = new SignallingImpl[U]
      subscribe { v =>
        val u = f(v)
        mappedSignal.emit(u)
      }
      mappedSignal

    override def close(): Unit = synchronized:
      closed = true
      subscribers.clear()
      onCloseCallbacks.foreach(_.apply)
      onCloseCallbacks = Nil

    def emit(value: V): Unit =
      val condidates = synchronized:
        subscribers.values.toList
      condidates.foreach { cb =>
        Future:
          try cb(value)
          catch case _: Throwable => ()
      }

    def subscribe(callback: V -> Unit): Subscription =
      val subscriberId = synchronized:
        val c = counter
        counter += 1
        subscribers += (c -> callback)
        c
      new Subscription:
        def cancel(): Unit = synchronized:
          subscribers -= subscriberId
