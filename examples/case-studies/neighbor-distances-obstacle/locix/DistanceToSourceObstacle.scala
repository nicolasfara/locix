package io.github.nicolasfara.locix

import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.peers.Peers.*
import io.github.locix.network.Network
import io.github.locix.placement.*
import io.github.locix.raise.Raise
import io.github.locix.network.NetworkError
import io.github.locix.handlers.*
import io.github.locix.distributed.InMemoryNetwork
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor
import io.github.locix.network.Network.peerAddress
import io.github.locix.placement.PlacementType.on
import io.github.locix.placement.PeerScope.take
import io.github.locix.Collective.*
import io.github.locix.CollectiveBuildingBlocks.distanceTo
import scala.concurrent.*

object DistanceToSourceObstacle:
  type Node <: { type Tie <: Multiple[Node] }

  private val devicePositions = Map(
    "node-1" -> (0.0, 0.0),
    "node-2" -> (1.5, 0.0),
    "node-3" -> (0.0, 1.0),
    "node-4" -> (1.0, 1.0),
  )

  def neighborDistanceObstacle(isObstacle: Boolean, isSource: Boolean)(using Network, Placement, Collective, DistanceSensor) =
    val neighborCounter = Collective[Node](1.seconds):
      branch(isObstacle) { Double.PositiveInfinity } { distanceTo(isSource) }

    on[Node]:
      val signal = take(neighborCounter)
      signal.subscribe { value =>
        println(s"Device ${peerAddress} has count: $value")
      }
      Thread.sleep(5000) // Keep the program running for a while to observe the output

  def neighborDistanceObstacleApp(using Network, Placement, Collective) =
    given DistanceSensor = new DistanceSensor:
      val localPeer = peerAddress.asInstanceOf[String]
      def nbrRange(using Collective, VM): Field[Double] =
        val localPos = devicePositions(localPeer)
        nbr(localPos).map { position =>
          math.sqrt(
            math.pow(localPos._1 - position._1, 2) +
              math.pow(localPos._2 - position._2, 2),
          )
        }

    neighborDistanceObstacle(peerAddress == "node-2" || peerAddress == "node-3", peerAddress == "node-1")

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Collective) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: Placement = PlacementTypeHandler.handler[P]
    given clHandler: Collective = CollectiveHandler.handle[P, net.PeerAddress, V]
    program

  def main(args: Array[String]): Unit =
    println("Running Channel collective example...")
    val broker = InMemoryNetwork.broker()
    val node1 = InMemoryNetwork[Node]("node-1", broker)
    val node2 = InMemoryNetwork[Node]("node-2", broker)
    val node3 = InMemoryNetwork[Node]("node-3", broker)
    val node4 = InMemoryNetwork[Node]("node-4", broker)

    val futures = Seq(node1, node2, node3, node4).map { net =>
      Future:
        handleProgramForPeer[Node](net)(neighborDistanceObstacleApp)
    }
    Await.result(Future.sequence(futures), Duration.Inf)
end DistanceToSourceObstacle
