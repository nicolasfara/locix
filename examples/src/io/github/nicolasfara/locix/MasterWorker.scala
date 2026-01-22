package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.duration.*

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{ Locix, Multitier }
import ox.flow.Flow

import Multitier.*
import network.Network.*
import placement.Peers.Quantifier.*
import placement.Peers.peer
import placement.PlacedFlow
import placement.PlacedFlow.*
import placement.PlacedValue
import placement.PlacedValue.*

object MasterWorker:
  type Worker <: { type Tie <: Single[Master] }
  type Master <: { type Tie <: Multiple[Worker] }

  case class Task(val input: Int):
    def exec(): Int = input * input

  def selectWorker(using net: Network): net.effect.Address[Worker] =
    import scala.util.Random
    val reachablePeers = reachablePeersOf[Worker].toList
    val candidate = Random.shuffle(reachablePeers).head
    candidate

  def masterWorker(using Network, Multitier, PlacedFlow, PlacedValue) =
    val inputsOnMaster = flowOn[Master]:
      println(s"[$localAddress] generating tasks on Master.")
      Flow.fromIterable(List(1, 2, 3, 4, 5)).map(selectWorker -> Task(_))

    val resultOnWorker = on[Worker]:
      println(s"[$localAddress] started processing tasks.")
      val tasks = collectAsLocal(inputsOnMaster)
        .filter((addr, _) => addr == localAddress)
        .tap((idTask => println(s"[$localAddress] received task: ${idTask._2}")))
        .map((id, task) => task.exec())
        .runToList()
      println(s"[$localAddress] completed tasks with results: ${tasks}")
      tasks

    on[Master]:
      val workerResults = asLocalAll(resultOnWorker)
      println(s"[$localAddress] collected results from workers: ${workerResults}")
      val collectedResults = workerResults.values.flatten
      println(s"[$localAddress] Final results collected: ${collectedResults.toList.sorted}")
  end masterWorker

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val masterNetwork = InMemoryNetwork[Master]("master-address", 0)
    val worker1Network = InMemoryNetwork[Worker]("worker-address-1", 1)
    val worker2Network = InMemoryNetwork[Worker]("worker-address-2", 2)
    val worker3Network = InMemoryNetwork[Worker]("worker-address-3", 3)
    masterNetwork.addReachablePeer(worker1Network)
    masterNetwork.addReachablePeer(worker2Network)
    masterNetwork.addReachablePeer(worker3Network)
    worker1Network.addReachablePeer(masterNetwork)
    worker2Network.addReachablePeer(masterNetwork)
    worker3Network.addReachablePeer(masterNetwork)

    val masterFuture = Future:
      println("Starting Master")
      given Locix[InMemoryNetwork[Master]] = Locix(masterNetwork)
      PlacedValue.run[Master]:
        PlacedFlow.run[Master]:
          Multitier.run[Master](masterWorker)
          ()

    val worker1Future = Future:
      println("Starting Worker 1")
      given Locix[InMemoryNetwork[Worker]] = Locix(worker1Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val worker2Future = Future:
      println("Starting Worker 2")
      given Locix[InMemoryNetwork[Worker]] = Locix(worker2Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val worker3Future = Future:
      println("Starting Worker 3")
      given Locix[InMemoryNetwork[Worker]] = Locix(worker3Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val complete = Future.sequence(List(masterFuture, worker1Future, worker2Future, worker3Future))
    Await.result(complete, Duration.Inf)
  end main
end MasterWorker
