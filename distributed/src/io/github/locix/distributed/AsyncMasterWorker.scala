package io.github.locix.distributed

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.github.locix.Multitier
import io.github.locix.Multitier.*
import io.github.locix.handlers.MultitierHandler
import io.github.locix.handlers.PlacementTypeHandler
import io.github.locix.network.Network
import io.github.locix.network.Network.peerAddress
import io.github.locix.network.NetworkError
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.peers.Peers.Peer
import io.github.locix.peers.Peers.PeerTag
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.on
import io.github.locix.raise.Raise
import io.github.locix.signal.Signal
import io.github.locix.signal.Signal.signalBuilder

object AsyncMasterWorker:
  type Master <: { type Tie <: Multiple[Worker] }
  type Worker <: { type Tie <: Single[Master] }

  def asyncMasterWorker(using Network, Placement, Multitier) = Multitier:
    val signals = on[Master]:
      signalBuilder[Int]: e =>
        Thread.sleep(1000) // Simulate some work before emitting the signal
        e.emit(42)
        Thread.sleep(1000) // Simulate some work before emitting the signal
        e.emit(42)
        Thread.sleep(1000) // Simulate some work before emitting the signal
        e.emit(42)
        Thread.sleep(1000) // Simulate some work before emitting the signal
        e.emit(42)
    on[Worker]:
      val localAddress = peerAddress
      val signal = asLocal(signals)
      println(s"Peer [$localAddress] - ${signal.fold(0)(_ + _)}")

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Multitier) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: Placement = PlacementTypeHandler.handler[P]
    given mtHandler: Multitier = MultitierHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running Master-Worker task allocation...")
    val broker = InMemoryNetwork.broker()
    val masterNetwork = InMemoryNetwork[Master]("master", broker)
    val workerNetwork1 = InMemoryNetwork[Worker]("worker1", broker)
    val workerNetwork2 = InMemoryNetwork[Worker]("worker2", broker)
    val workerNetwork3 = InMemoryNetwork[Worker]("worker3", broker)

    // Run the task allocation program on all peers
    val masterFuture = Future { handleProgramForPeer[Master](masterNetwork)(asyncMasterWorker) }
    val workerFutures = Seq(workerNetwork1, workerNetwork2, workerNetwork3).map { net =>
      Future { handleProgramForPeer[Worker](net)(asyncMasterWorker) }
    }

    // Wait for all peers to finish
    val combinedFuture = Future.sequence(masterFuture +: workerFutures)
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
end AsyncMasterWorker
