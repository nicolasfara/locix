package io.github.nicolasfara.locix.distributed

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.network.Network.reachablePeersOf
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.network.Network.peerAddress
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.handlers.MultitierHandler
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.nicolasfara.locix.placement.PlacementType

object MasterWorker:
  type Master <: { type Tie <: Multiple[Worker] }
  type Worker <: { type Tie <: Single[Master] }

  class Task(input: Int):
    def exec(): Int = input * 2

  def allocateTasks(using Network) =
    val inputs = List(1, 2, 3, 4, 5)
    val workers = reachablePeersOf[Worker]
    val allocation = workers
      .zip(inputs.grouped((inputs.size + workers.size - 1) / workers.size))
      .toMap
      .map:
        case (worker, tasks) => worker -> tasks.map(new Task(_))
    allocation

  def taskAllocation(using n: Network, p: Placement, m: Multitier) = Multitier:
    val allocation = on[Master] { allocateTasks }
    val workerResults = on[Worker]:
      val localAddress = peerAddress
      asLocal(allocation)
        .filter(_._1 == localAddress)
        .flatMap(_._2)
        .map(task => task.exec())
        .tapEach(result => println(s"[$localAddress] computed result: $result"))
    on[Master]:
      val collectedResults = asLocalAll(workerResults).values.flatMap(_.toList).toList
      println(s"Master collected results: $collectedResults")
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
    val masterFuture = Future { handleProgramForPeer[Master](masterNetwork)(taskAllocation) }
    val workerFutures = Seq(workerNetwork1, workerNetwork2, workerNetwork3).map { net =>
      Future { handleProgramForPeer[Worker](net)(taskAllocation) }
    }

    // Wait for all peers to finish
    val combinedFuture = Future.sequence(masterFuture +: workerFutures)
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
end MasterWorker
