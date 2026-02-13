package io.github.nicolasfara.locix.distributed

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.placement.PlacementType.on
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.handlers.MultitierHandler
import scala.concurrent.Future
import scala.concurrent.Await
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.signal.Signal
import io.github.nicolasfara.locix.signal.Signal.signalBuilder
import io.github.nicolasfara.locix.network.Network.peerAddress

object AsyncMasterWorker:
  type Master <: { type Tie <: Multiple[Worker] }
  type Worker <: { type Tie <: Single[Master] }

  def asyncMasterWorker(using Network, Placement, Multitier) = Multitier:
    val signals = on[Master]:
      signalBuilder: e =>
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
      signal.subscribe(value => println(s"Worker [$localAddress] received value: $value"))
      // if localAddress == "worker1" then signal.subscribe(value => println(s"MUUU"))
    Thread.sleep(5000) // Wait for a bit to ensure the signal is received before the program exits
    ()

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
