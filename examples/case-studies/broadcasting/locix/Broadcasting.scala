package io.github.locix

import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.network.Network
import io.github.locix.placement.Placement
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.locix.peers.Peers.Peer
import io.github.locix.peers.Peers.PeerTag
import io.github.locix.placement.PlacementType
import io.github.locix.raise.Raise
import io.github.locix.network.NetworkError
import io.github.locix.handlers.*
import io.github.locix.distributed.InMemoryNetwork
import scala.concurrent.duration.Duration
import io.github.locix.network.Network.peerAddress
import io.github.locix.placement.PlacementType.*
import io.github.locix.placement.PeerScope.*
import io.github.locix.CollectiveBuildingBlocks.*
import io.github.locix.Collective.nbr
import scala.concurrent.*
import NbrSensor.* 
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor.nbrRange

object Broadcasting:
  type Node <: { type Tie <: Multiple[Node] }

  private val devicePositions = Map(
    "node-1" -> (0.0, 0.0),
    "node-2" -> (1.5, 0.0),
    "node-3" -> (0.0, 1.0),
    "node-4" -> (1.0, 1.0),
  )

  def broadcasting(source: Boolean)(using Network, Collective, Placement, DistanceSensor) =
    val id = peerAddress
    Collective[Node](1.second):
      G(source, id, identity, () => nbrRange)

  def broadcastingApp(using Network, Placement, Collective) =
    given DistanceSensor = sensor(peerAddress.asInstanceOf[String], devicePositions)
    val broadcastSignal = broadcasting(peerAddress == "node-1")
    val unitOf = on[Node]:
      take(broadcastSignal).subscribe { source =>
        println(s"[$peerAddress] Received broadcast from source: $source")
      }
    Thread.sleep(5000) // Keep the application running for a while to observe the output

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
        handleProgramForPeer[Node](net)(broadcastingApp)
    }
    Await.result(Future.sequence(futures), Duration.Inf)
end Broadcasting
