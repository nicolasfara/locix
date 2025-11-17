package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.placement.Peers.Quantifier.*
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.PlacedFlow
import io.github.nicolasfara.locicope.placement.PlacedFlow.{ take, PlacedFlow }
import io.github.nicolasfara.locicope.Collective.{ collective, Collective }
import io.github.nicolasfara.locicope.CirceCodec.given
import scala.concurrent.duration.DurationInt
import io.github.nicolasfara.locicope.Collective.repeat
import io.github.nicolasfara.locicope.network.InMemoryNetwork
import io.github.nicolasfara.locicope.placement.Peers.peer
import scala.concurrent.Future
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.Multitier.collectAsLocalAll
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.network.Network.localAddress
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import io.github.nicolasfara.locicope.Collective.neighbors
import io.github.nicolasfara.locicope.FieldOps.sum

object AggregateCounter:
  type Smartphone <: { type Tie <: Multiple[Smartphone] }

  def counter(using Network, Collective, PlacedFlow, PlacedValue) =
    val collectiveCounters = collective[Smartphone](1.seconds):
      repeat(0): _ =>
        neighbors(1).sum

    on[Smartphone]:
      val counters = collectiveCounters.take
      counters
        .take(10)
        .runForeach: count =>
          println(s"Smartphone $localAddress counter: $count")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val smartphone1 = InMemoryNetwork(peer[Smartphone], "smartphone-1", 1)
    val smartphone2 = InMemoryNetwork(peer[Smartphone], "smartphone-2", 2)
    val smartphone3 = InMemoryNetwork(peer[Smartphone], "smartphone-3", 3)

    smartphone1.addReachablePeer(smartphone2)
    smartphone1.addReachablePeer(smartphone3)
    smartphone2.addReachablePeer(smartphone1)
    smartphone3.addReachablePeer(smartphone1)

    val smartphone1Future = Future:
      println("Starting Smartphone 1")
      given Locicope[Network.Effect] = Locicope[Network.Effect](smartphone1)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](counter)

    val smartphone2Future = Future:
      println("Starting Smartphone 2")
      given Locicope[Network.Effect] = Locicope[Network.Effect](smartphone2)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](counter)

    val smartphone3Future = Future:
      println("Starting Smartphone 3")
      given Locicope[Network.Effect] = Locicope[Network.Effect](smartphone3)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](counter)

    val complete = Future.sequence(List(smartphone1Future, smartphone2Future, smartphone3Future))
    Await.result(complete, Duration.Inf)
    println("AggregateCounter program completed")
  end main
end AggregateCounter
