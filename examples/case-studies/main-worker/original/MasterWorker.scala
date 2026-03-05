package batch.masterworker

import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import loci.serializer.upickle._
import loci.transmitter.IdenticallyTransmittable

import rescala.default._

import upickle.default._


case class Task(v: Int) { def exec: Int = 2 * v }

object Task {
  implicit val taskTransmittable: IdenticallyTransmittable[Task] = IdenticallyTransmittable()
  implicit val taskSerializer: ReadWriter[Task] = macroRW[Task]
}

@multitier object MasterWorker {
  @peer type Master <: { type Tie <: Multiple[Worker] }
  @peer type Worker <: { type Tie <: Single[Master] }

  val taskStream: Local[Evt[Task]] on Master = Evt[Task]()

  val assocs: Local[Signal[Map[Remote[Worker], Task]]] on Master =
    ((taskStream || taskResult.asLocalFromAllSeq || remote[Worker].joined)
      .fold(Map.empty[Remote[Worker], Task] -> List.empty[Task]) { case ((taskAssocs, taskQueue), taskChanged) =>
         assignTasks(taskAssocs, taskQueue, taskChanged, remote[Worker].connected())
      }
      map { case (taskAssocs, _) => taskAssocs })

  val deployedTask = on[Master] sbj { worker: Remote[Worker] =>
    Signal { assocs().get(worker) }
  }

  val taskResult = on[Worker] {
    deployedTask.asLocal.changed collect { case Some(task) => task.exec }
  }

  val result = on[Master] {
    taskResult.asLocalFromAllSeq.fold(0){ case (acc, (_, result)) => acc + result }
  }

  def assignTasks(
      taskAssocs: Map[Remote[Worker], Task],
      taskQueue: List[Task],
      taskChanged: AnyRef,
      connected: Seq[Remote[Worker]]) = on[Master] local {
    def assignFreeWorker(assocs: Map[Remote[Worker], Task], task: Task) =
      (connected filterNot { assocs contains _ }).headOption map { worker =>
        assocs + (worker -> task)
      }

    def assignQueuedTask(assocs: Map[Remote[Worker], Task]) =
      taskQueue.headOption flatMap { task =>
        assignFreeWorker(assocs, task) map { _ -> taskQueue.tail }
      } getOrElse assocs -> taskQueue

    taskChanged match {
      case (worker: Remote[Worker @unchecked], _) =>
        assignQueuedTask(taskAssocs - worker)

      case _: Remote[_] =>
        assignQueuedTask(taskAssocs)

      case task: Task =>
        assignFreeWorker(taskAssocs, task) map { _ -> taskQueue } getOrElse
          taskAssocs -> (taskQueue :+ task)

      case _ =>
        taskAssocs -> taskQueue
    }
  }

  def main() = on[Master] {
    var count = 0
    result observe { result =>
      println(s"current result value: $result")
      count += 1
      if (count > 3)
        multitier.terminate()
    }

    taskStream.fire(Task(5))
    taskStream.fire(Task(7))
    taskStream.fire(Task(9))
  }
}


object MasterWorkerMain extends App {
  multitier start new Instance[MasterWorker.Master](
    listen[MasterWorker.Worker] { TCP(1095) })

  1 to 2 foreach { _ =>
    multitier start new Instance[MasterWorker.Worker](
      connect[MasterWorker.Master] { TCP("localhost", 1095) })
  }
}
