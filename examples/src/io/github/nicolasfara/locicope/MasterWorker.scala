package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.placement.Peers.Quantifier.*
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.Multitier.Multitier
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.CirceCodec.given
import io.github.nicolasfara.locicope.Multitier.asLocalAll
import io.github.nicolasfara.locicope.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locicope.placement.PlacedFlow.flowOn
import ox.flow.Flow
import io.github.nicolasfara.locicope.network.Network.reachablePeersOf
import io.github.nicolasfara.locicope.network.Network.getId
import io.github.nicolasfara.locicope.Multitier.{ asLocal, collectAsLocal }
import io.github.nicolasfara.locicope.network.Network.localAddress

trait IntNetwork extends Network.Effect:
  override type Id = Int

trait MasterWorkerNetwork extends Locicope[IntNetwork]

object MasterWorker:
  type Worker <: { type Tie <: Single[Master] }
  type Master <: { type Tie <: Multiple[Worker] }

  case class Task(val input: Int):
    def exec(): Int = input * input

  def selectWorker(using net: MasterWorkerNetwork): Int =
    import scala.util.Random
    val reachablePeers = reachablePeersOf[Worker]
    val candidate = Random.shuffle(reachablePeers).head
    getId(candidate)

  def masterWorker(using MasterWorkerNetwork, Multitier, PlacedFlow, PlacedValue) =
    val inputsOnMaster = flowOn[Master]:
      Flow.fromIterable(List(1, 2, 3, 4, 5)).map(input => selectWorker -> Task(input))

    val resultOnWorker = on[Worker]:
      val tasks = collectAsLocal(inputsOnMaster)
        .filter((id, _) => id == getId(localAddress))
        .map((id, task) => task.exec())
        .runToList()
      tasks

    on[Master]:
      val workerResults = asLocalAll(resultOnWorker)
      val collectedResults = workerResults.values.flatten
      println(s"Final results collected at Master: ${collectedResults.toList}")
end MasterWorker
