package io.github.nicolasfara.locix

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

import io.github.nicolasfara.locix.CirceCodec.given
import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{Collective, Locix}

import Collective.*
import FieldOps.sum
import network.Network
import network.Network.*
import placement.Peers.Quantifier.*
import placement.Peers.peer
import placement.PlacedFlow
import placement.PlacedFlow.*
import placement.PlacedValue
import placement.PlacedValue.*

object AggregateCounter:
  type Smartphone <: { type Tie <: Multiple[Smartphone] }

  def neighborCounter(using Network, Collective, PlacedFlow, PlacedValue) =
    val collectiveCounters = collective[Smartphone](1.seconds):
      repeat(0): _ =>
        neighbors(1).sum

    on[Smartphone]:
      val counter = collectiveCounters.take
      counter
        .take(10)
        .runForeach: count =>
          println(s"[$localAddress] counter: $count")

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
      given Locix[Network.Effect] = Locix[Network.Effect](smartphone1)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](neighborCounter)

    val smartphone2Future = Future:
      println("Starting Smartphone 2")
      given Locix[Network.Effect] = Locix[Network.Effect](smartphone2)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](neighborCounter)

    val smartphone3Future = Future:
      println("Starting Smartphone 3")
      given Locix[Network.Effect] = Locix[Network.Effect](smartphone3)
      PlacedFlow.run[Smartphone]:
        PlacedValue.run[Smartphone]:
          Collective.run[Smartphone](neighborCounter)

    val complete = Future.sequence(List(smartphone1Future, smartphone2Future, smartphone3Future))
    Await.result(complete, Duration.Inf)
    println("AggregateCounter program completed")
  end main
end AggregateCounter
