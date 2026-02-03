package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.placement.PlacedFlow
import io.github.nicolasfara.locix.placement.PlacedFlow.*
import placement.PlacedValue
import placement.PlacedValue.*
import io.github.nicolasfara.locix.network.Network.*
import Collective.*
import scala.concurrent.duration.DurationInt
import io.github.nicolasfara.locix.AggregateBuildingBlocks.distanceTo
import io.github.nicolasfara.locix.AggregateBuildingBlocks.distanceBetween
import io.github.nicolasfara.locix.AggregateBuildingBlocks.DistanceSensor
import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.FieldOps.*
import io.github.nicolasfara.locix.placement.PlacementType.Placed
import io.github.nicolasfara.locix.AggregateBuildingBlocks.G

object Channel:
  type Node <: { type Tie <: Multiple[Node] }

  // Arrange devices in a square grid
  private val devicePositions = Map(
    "node-1" -> (0.0, 0.0),
    "node-2" -> (1.5, 0.0),
    "node-3" -> (0.0, 1.0),
    "node-4" -> (1.0, 1.0),
  )

  def channel(using Network, Collective, PlacedFlow, DistanceSensor)(source: Boolean, target: Boolean, width: Int) = collective[Node](1.second):
    distanceTo(source) + distanceTo(target) <= distanceBetween(source, target)

  def channelApp(using Network, Collective, PlacedFlow, PlacedValue) =
    given DistanceSensor with
      def nbrRange(using coll: Collective, vm: coll.effect.VM^): vm.Field[Double] =
        val localPos = devicePositions(localAddress[Node].asInstanceOf[String])
        // Compute distances to neighbors
        neighbors(localPos).map { position =>
          math.sqrt(
            math.pow(localPos._1 - position._1, 2) +
            math.pow(localPos._2 - position._2, 2)
          )
        }
    val channelFlow = channel(localAddress == "node-1", localAddress == "node-4", 1)
    val unitOf = on[Node]:
      val channels = channelFlow.takeFlow
      channels.runForeach { isInChannel =>
        println(s"[$localAddress] is in channel: $isInChannel")
      }
  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val node1 = InMemoryNetwork[Node]("node-1", 1)
    val node2 = InMemoryNetwork[Node]("node-2", 2)
    val node3 = InMemoryNetwork[Node]("node-3", 3)
    val node4 = InMemoryNetwork[Node]("node-4", 4)

    // Square grid connections:
    // 3 --- 4
    // |      \
    // 1 ----- 2
    node1.addReachablePeer(node2) // 1 -> 2
    node1.addReachablePeer(node3) // 1 -> 3
    node2.addReachablePeer(node1) // 2 -> 1
    node2.addReachablePeer(node4) // 2 -> 4
    node3.addReachablePeer(node1) // 3 -> 1
    node3.addReachablePeer(node4) // 3 -> 4
    node4.addReachablePeer(node2) // 4 -> 2
    node4.addReachablePeer(node3) // 4 -> 3

    val devices = List(node1, node2, node3, node4).map { node =>
      scala.concurrent.Future:
        println(s"Starting ${node.localAddress}")
        given Locix[InMemoryNetwork[Node]] = Locix(node)
        PlacedValue.run[Node]:
          PlacedFlow.run[Node]:
            Collective.run[Node]:
              Multitier.run[Node](channelApp)
    }

    val allFutures = scala.concurrent.Future.sequence(devices)
    scala.concurrent.Await.result(allFutures, scala.concurrent.duration.Duration.Inf)
    