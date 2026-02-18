package io.github.nicolasfara.locix

import placement.Peers.Quantifier.*
import network.Network.*
import placement.PlacedFlow.*
import Multitier.*
import placement.PlacedValue.*
import placement.PlacedValue
import placement.PlacementType.*
import placement.PlacedFlow
import ox.*
import java.util.UUID
import ox.flow.Flow
import ox.flow.FlowStage
import ox.channels.Channel
import io.github.nicolasfara.locix.network.InMemoryNetwork
import scala.concurrent.Future
import scala.concurrent.Await

object MultitierChat:
  type Prev <: { type Tie <: Single[Prev] }
  type Next <: { type Tie <: Single[Next] }
  type Node <: Prev & Next { type Tie <: Single[Prev] & Single[Next] }

  val id = java.util.UUID.randomUUID()
  
  def tokenRing(using Network, PlacedFlow, PlacedValue, Multitier) = 
    val tokenRingOnNode = on[Prev] { Channel.bufferedDefault[(UUID, String)] }
    val flow: Flow[String] on Prev = flowOn[Prev]:
      val channel = tokenRingOnNode.take
      Flow.fromSource(channel)/*.filter { (receiver, token) => receiver != id }*/.map(_._2)
    on[Node]:
      val f = collectAsLocal[Prev, Prev, String](flow)
      val taks = supervised:
        fork:
          f.runForeach { elem => println("Received message: " + elem) }
      val localId = java.util.UUID.randomUUID()
      tokenRingOnNode.take.send((localId, s"Token for ${localId.toString}"))
      taks.join()

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val node1 = InMemoryNetwork[Node]("address-1", 1)
    val node2 = InMemoryNetwork[Node]("address-2", 2)
    val node3 = InMemoryNetwork[Node]("address-3", 3)
    
    node1.addReachablePeer(node2)
    node2.addReachablePeer(node3)
    node3.addReachablePeer(node1)

    val node1Future = Future:
      println("Starting Node 1")
      given Locix[InMemoryNetwork[Node]] = Locix(node1)
      PlacedValue.run[Node]:
        PlacedFlow.run[Node]:
          Multitier.run[Node](tokenRing)
    val node2Future = Future:
      println("Starting Node 2")
      given Locix[InMemoryNetwork[Node]] = Locix(node2)
      PlacedValue.run[Node]:
        PlacedFlow.run[Node]:
          Multitier.run[Node](tokenRing)
    val node3Future = Future:
      println("Starting Node 3")
      given Locix[InMemoryNetwork[Node]] = Locix(node3)
      PlacedValue.run[Node]:
        PlacedFlow.run[Node]:
          Multitier.run[Node](tokenRing)
    val complete = Future.sequence(List(node1Future, node2Future, node3Future))
    Await.result(complete, scala.concurrent.duration.Duration.Inf)
