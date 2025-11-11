package io.github.nicolasfara.locicope

import scala.concurrent.duration.FiniteDuration
import scala.annotation.nowarn

import ox.flow.Flow
import ox.flow.FlowEmit
import ox.flow.FlowStage
import ox.forever

object FlowOps:
  @nowarn inline private def usingEmitInline[T](inline withEmit: FlowEmit[T] => Unit): Flow[T] = Flow(
    new FlowStage:
      override def run(emit: FlowEmit[T]): Unit = withEmit(emit),
  )

  inline def onEvery[T](interval: FiniteDuration)(inline value: => T): Flow[T] = usingEmitInline: emit =>
    forever:
      val start = System.nanoTime()
      emit(value)
      val end = System.nanoTime()
      val sleep = interval.toNanos - (end - start)
      if sleep > 0 then Thread.sleep(sleep / 1_000_000, (sleep % 1_000_000).toInt)
