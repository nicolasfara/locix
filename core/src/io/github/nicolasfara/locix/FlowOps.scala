package io.github.nicolasfara.locix

import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration

import ox.flow.*
import ox.forever

object FlowOps:
  @nowarn private def usingEmitInline[T](withEmit: FlowEmit[T] => Unit): Flow[T] = Flow(
    new FlowStage:
      override def run(emit: FlowEmit[T]): Unit = withEmit(emit),
  )

  def onEvery[T](interval: FiniteDuration)(value: => T): Flow[T] = usingEmitInline: emit =>
    forever:
      val start = System.nanoTime()
      emit(value)
      val end = System.nanoTime()
      val sleep = interval.toNanos - (end - start)
      if sleep > 0 then Thread.sleep(sleep / 1_000_000, (sleep % 1_000_000).toInt)
