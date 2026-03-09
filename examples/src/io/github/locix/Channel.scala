package io.github.locix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationDouble

import io.github.locix.Collective
import io.github.locix.Collective.*
import io.github.locix.distributed.InMemoryNetwork
import io.github.locix.handlers.CollectiveHandler
import io.github.locix.handlers.PlacementTypeHandler
import io.github.locix.network.Network
import io.github.locix.network.Network.peerAddress
import io.github.locix.network.NetworkError
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.peers.Peers.Peer
import io.github.locix.peers.Peers.PeerTag
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.on
import io.github.locix.raise.Raise
import io.github.locix.CollectiveBuildingBlocks.*
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor
import scala.collection.concurrent.TrieMap

object Channel:
  type Node <: { type Tie <: Multiple[Node] }

  /* 
    node-1 (0,0) ------- node-2 (1.5, 0)
      |          \       /
    node-3 (0,1) - node-4 (1,1)
   */
  private val devicePositions = TrieMap(
      "node-1" -> (0.0, 0.0),
      "node-2" -> (1.5, 0.0),
      "node-3" -> (0.0, 1.0),
      "node-4" -> (1.0, 1.0),
  )

  def channel(source: Boolean, target: Boolean, width: Int)(using Network, Collective, PlacementType^, DistanceSensor) = Collective[Node](0.5.second):
    distanceTo(source) + distanceTo(target) <= distanceBetween(source, target)

  def channelApp(using Network, Placement, Collective) =
    given DistanceSensor = new DistanceSensor:
      val localPeer = peerAddress.asInstanceOf[String]
      def nbrRange(using Collective, VM): Field[Double] =
        val localPos = devicePositions(localPeer)
        nbr(localPos).map { position =>
          math.sqrt(
            math.pow(localPos._1 - position._1, 2) +
            math.pow(localPos._2 - position._2, 2)
          )
        }
    val channelSignal = channel(peerAddress == "node-1", peerAddress == "node-4", 1)
    val unitOf = on[Node]:
      val channels = take(channelSignal)
      channels.subscribe { isInChannel =>
        println(s"[$peerAddress] is in channel: $isInChannel")
      }
    Thread.sleep(5000) // Keep the application running to observe outputs

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
      scala.concurrent.Future:
        handleProgramForPeer[Node](net)(channelApp)
    }
    scala.concurrent.Await.result(scala.concurrent.Future.sequence(futures), Duration.Inf)
end Channel
