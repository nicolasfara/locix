package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.placement.Peers.Quantifier.*
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.Multitier.Multitier
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedFlow
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.CirceCodec.given
import io.github.nicolasfara.locicope.Multitier.asLocalAll
import io.github.nicolasfara.locicope.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locicope.placement.PlacedFlow.flowOn
import ox.flow.Flow
import io.github.nicolasfara.locicope.network.Network.reachablePeersOf
import io.github.nicolasfara.locicope.network.Network.getId
import io.github.nicolasfara.locicope.Multitier.collectAsLocal
import io.github.nicolasfara.locicope.network.Network.localAddress
import io.github.nicolasfara.locicope.network.InMemoryNetwork
import io.github.nicolasfara.locicope.placement.Peers.peer

type MasterWorkerNetwork = Locicope[InMemoryNetwork]

object MasterWorker:
  type Worker <: { type Tie <: Single[Master] }
  type Master <: { type Tie <: Multiple[Worker] }

  case class Task(val input: Int):
    def exec(): Int = input * input

  def selectWorker(using net: MasterWorkerNetwork): String =
    import scala.util.Random
    val reachablePeers = reachablePeersOf[Master]
    val candidate = Random.shuffle(reachablePeers).head
    candidate

  def masterWorker(using MasterWorkerNetwork, Multitier, PlacedFlow, PlacedValue) =
    val inputsOnMaster = flowOn[Master]:
      Flow.fromIterable(List(1, 2, 3, 4, 5)).map(selectWorker -> Task(_))

    val resultOnWorker = on[Worker]:
      println("Worker started processing tasks.")
      val tasks = collectAsLocal(inputsOnMaster)
        .filter((addr, _) => addr == localAddress)
        .tap((idTask => println(s"Worker ${getId(localAddress)} received task: ${idTask._2}")))
        .map((id, task) => task.exec())
        .runToList()
      println(s"Worker ${getId(localAddress)} completed tasks with results: ${tasks}")
      tasks

    on[Master]:
      val workerResults = asLocalAll(resultOnWorker)
      println(s"Master collected results from workers: ${workerResults}")
      val collectedResults = workerResults.values.flatten
      println(s"Final results collected at Master: ${collectedResults.toList.sorted}")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val masterNetwork = InMemoryNetwork(peer[Master], "master-address", 0)
    val worker1Network = InMemoryNetwork(peer[Worker], "worker-address1", 1)
    val worker2Network = InMemoryNetwork(peer[Worker], "worker-address2", 2)
    val worker3Network = InMemoryNetwork(peer[Worker], "worker-address3", 3)
    masterNetwork.addReachablePeer(worker1Network)
    masterNetwork.addReachablePeer(worker2Network)
    masterNetwork.addReachablePeer(worker3Network)
    worker1Network.addReachablePeer(masterNetwork)
    worker2Network.addReachablePeer(masterNetwork)
    worker3Network.addReachablePeer(masterNetwork)

    val masterFuture = scala.concurrent.Future:
      println("Starting Master")
      given Locicope[InMemoryNetwork] = Locicope[InMemoryNetwork](masterNetwork)
      PlacedValue.run[Master]:
        PlacedFlow.run[Master]:
          Multitier.run[Master](masterWorker)
          ()

    val worker1Future = scala.concurrent.Future:
      println("Starting Worker 1")
      given Locicope[InMemoryNetwork] = Locicope[InMemoryNetwork](worker1Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val worker2Future = scala.concurrent.Future:
      println("Starting Worker 2")
      given Locicope[InMemoryNetwork] = Locicope[InMemoryNetwork](worker2Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val worker3Future = scala.concurrent.Future:
      println("Starting Worker 3")
      given Locicope[InMemoryNetwork] = Locicope[InMemoryNetwork](worker3Network)
      PlacedValue.run[Worker]:
        PlacedFlow.run[Worker]:
          Multitier.run[Worker](masterWorker)
          ()

    val complete = scala.concurrent.Future.sequence(List(masterFuture, worker1Future, worker2Future, worker3Future))
    scala.concurrent.Await.result(complete, scala.concurrent.duration.Duration.Inf)
  end main
end MasterWorker
