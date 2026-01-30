package io.github.nicolasfara.locix

import placement.Peers.Quantifier
import network.Network.* 
import Collective.*
import scala.concurrent.duration.DurationInt
import io.github.nicolasfara.locix.placement.PlacedFlow.*
import FieldOps.*
import scala.concurrent.Await
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.*

object DistanceTo:
  type Node <: { type Tie <: Quantifier.Multiple[Node] }

  private val devicePositions = Map(
    "node-1" -> (0.0, 0.0),
    "node-2" -> (3.0, 4.0),
    "node-3" -> (6.0, 8.0),
    "node-4" -> (9.0, 12.0),
  )

  def nbrRange(using net: Network, c: Collective, vm: c.effect.VM^): vm.Field[Double] =
    val localPos = devicePositions(localAddress[Node].asInstanceOf[String])
    // Compute distances to neighbors
    neighbors(localPos).map { position =>
      math.sqrt(
        math.pow(localPos._1 - position._1, 2) +
        math.pow(localPos._2 - position._2, 2)
      )
    }

  def distanceTo(isSource: Boolean)(using Network, Collective, PlacedFlow) = collective[Node](1.second):
    repeat(Double.PositiveInfinity): dist =>
      mux(isSource) { 0.0 } { (neighbors(dist) + nbrRange).min }

  def distanceToApp(using Network, Collective, PlacedFlow, PlacedValue) =
    val distanceFlow = distanceTo(localAddress == "node-4")
    val unitOf = on[Node]:
      val distances = distanceFlow.takeFlow
      distances.runForeach { distance =>
        println(s"[$localAddress] distance to source: $distance")
      }

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global
    import io.github.nicolasfara.locix.network.InMemoryNetwork
    import io.github.nicolasfara.locix.{ Choreography, Locix }
    import placement.PlacedFlow
    import placement.PlacedFlow.*

    val node1 = InMemoryNetwork[Node]("node-1", 1)
    val node2 = InMemoryNetwork[Node]("node-2", 2)
    val node3 = InMemoryNetwork[Node]("node-3", 3)
    val node4 = InMemoryNetwork[Node]("node-4", 4)

    node1.addReachablePeer(node2)
    node2.addReachablePeer(node1)
    node2.addReachablePeer(node3)
    node3.addReachablePeer(node2)
    node3.addReachablePeer(node4)
    node4.addReachablePeer(node3)

    val nodeFutures = List(
      (node1, false),
      (node2, false),
      (node3, false),
      (node4, true),
    ).map { case (network, isSource) =>
      scala.concurrent.Future:
        println(s"Starting node at address: ${network.localAddress}")
        given Locix[InMemoryNetwork[Node]] = Locix(network)
        PlacedFlow.run[Node]:
          PlacedValue.run[Node]:
            Collective.run[Node] { distanceToApp }
    }
    Await.result(scala.concurrent.Future.sequence(nodeFutures), scala.concurrent.duration.Duration.Inf)
  end main
