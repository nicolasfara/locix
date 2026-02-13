package io.github.nicolasfara.locix.signal

import io.github.nicolasfara.locix.network.Identifier
import scala.concurrent.ExecutionContext
import scala.collection.mutable
import scala.concurrent.Future
import java.util.concurrent.CountDownLatch
import scala.concurrent.Await
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.collection.concurrent.TrieMap
import io.github.nicolasfara.locix.network.Network
import scala.caps.CapSet
import scala.caps.unsafe.unsafeAssumePure

trait Emitter[V]:
  def emit(value: V): Unit
  def close(): Unit

trait Subscription:
  def cancel(): Unit

trait Signal[V]:
  def subscribe(callback: V => Unit): Subscription
  def onClose(callback: () -> Unit): Unit
  def map[U](f: V -> U): Signal[U]

object Signal:
  def make[V](using ec: ExecutionContext): (Signal[V], Emitter[V]) =
    val signal = new SignallingImpl[V]
    (signal, signal)

  def signalBuilder[V](body: Emitter[V] => Unit)(using ec: ExecutionContext): Signal[V] =
    val (signal, emitter) = make[V]
    ec.execute(() => body(emitter))
    signal

  class SignallingImpl[V](using ec: ExecutionContext) extends Signal[V], Emitter[V]:
    private val subscribers = TrieMap[Long, V -> Unit]()
    private val onCloseCallbacks = TrieMap.empty[Long, () -> Unit]
    private val counter = AtomicLong(0)
    private val callbackCounter = AtomicLong(0)
    private val closed = AtomicBoolean(false)

    override def onClose(callback: () -> Unit): Unit =
      if closed.get() then callback()
      else
        val id = callbackCounter.incrementAndGet()
        onCloseCallbacks += (id -> callback)
        // Double-check in case close() happened between the first check and adding the callback
        if closed.get() then onCloseCallbacks.remove(id).foreach(_.apply)

    override def map[U](f: V -> U): Signal[U] =
      val mappedSignal = new SignallingImpl[U]
      subscribe { v =>
        val u = f(v)
        mappedSignal.emit(u)
      }
      mappedSignal

    override def close(): Unit = if closed.compareAndSet(false, true) then
      subscribers.clear()
      val callbacks = onCloseCallbacks.values.toList
      onCloseCallbacks.clear()
      callbacks.foreach(_.apply)

    def emit(value: V): Unit = if !closed.get() then
      val candidates = subscribers.values.toList
      candidates.foreach { cb =>
        Future:
          try cb(value)
          catch case _: Throwable => ()
      }

    def subscribe(callback: V => Unit): Subscription =
      val id = counter.incrementAndGet()
      subscribers += (id -> unsafeAssumePure(callback))
      new Subscription:
        def cancel(): Unit =
          subscribers -= id
