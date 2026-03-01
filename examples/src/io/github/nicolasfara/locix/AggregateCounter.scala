package io.github.nicolasfara.locix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

import io.github.nicolasfara.locix.Collective
import io.github.nicolasfara.locix.Collective.*
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import io.github.nicolasfara.locix.handlers.CollectiveHandler
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Network.peerAddress
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.raise.Raise

object AggregateCounter:
  type Smartphone <: { type Tie <: Multiple[Smartphone] }

  private def counter(using Network, Placement, Collective) =
    val collectiveCounter = Collective[Smartphone](1.seconds):
      // rep(0)(_ + 1)
      val nbrCount = nbr(1)
      nbrCount.sum

    on[Smartphone]:
      val signal = take(collectiveCounter)
      signal.subscribe { value =>
        println(s"Device ${peerAddress} has count: $value")
      }
      Thread.sleep(5000) // Keep the program running for a while to observe the output

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Collective) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: Placement = PlacementTypeHandler.handler[P]
    given clHandler: Collective = CollectiveHandler.handle[P, net.PeerAddress, V]
    program

  def main(args: Array[String]): Unit =
    val broker = InMemoryNetwork.broker()
    val smartphone1 = InMemoryNetwork[Smartphone]("smartphone-1", broker)
    val smartphone2 = InMemoryNetwork[Smartphone]("smartphone-2", broker)

    val futures = Seq(smartphone1, smartphone2).map { net =>
      scala.concurrent.Future:
        handleProgramForPeer[Smartphone](net)(counter)
    }
    scala.concurrent.Await.result(scala.concurrent.Future.sequence(futures), Duration.Inf)
end AggregateCounter
