# LociX

> A flexible, multi-party and multi-paradigm capability-based framework.


## Examples

See the [examples](./examples) folder for various example applications built with LociX.

### Ping-Pong

```scala
object PingPong:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def pingPongProgram(using Network, Choreography, PlacedValue) =
    val ping = on[Pinger]("ping")
    val pingReceived = comm[Pinger, Ponger](ping)
    val pong = on[Ponger]:
      val receivedPing = pingReceived.take
      println(s"[$localAddress] received: $receivedPing")
      "pong"
    val pongReceived = comm[Ponger, Pinger](pong)
    val finalPing = on[Pinger]:
      val receivedPong = pongReceived.take
      println(s"[$localAddress] received: $receivedPong")
```

The Ping-Pong example demonstrates a simple interaction between two parties, `Pinger` and `Ponger`.
The `Pinger` sends a "ping" message to the `Ponger`, which responds with a "pong" message.
Each party prints the messages it receives along with its local address.

The architecture consists of two parties:
- `Pinger`: Initiates the communication by sending a "ping" message.
- `Ponger`: Waits for the "ping" message and responds with a "pong" message.

The `pingPongProgram` function defines the choreography of the interaction, specifying how messages are sent and received between the two parties.
From the method signature, we can see that it requires capabilities for networking, choreography, and placed values to function correctly.
The `Network` capability allows for communication between parties, `Choreography` manages the flow of interactions, and `PlacedValue` handles values that are specific to certain locations or parties.

### Master-Worker

```scala
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
```

The Master-Worker example illustrates a distributed computation model where a `Master` node delegates tasks to multiple `Worker` nodes.
The `Master` generates a list of tasks (in this case, squaring integers) and assigns them to available `Worker` nodes for processing.
Each `Worker` executes the tasks assigned to it and returns the results back to the `Master`, which collects and displays the final results.

The architecture consists of two types of parties:
- Single `Master`: Responsible for generating tasks and collecting results from workers.
- Multiple `Worker`s: Execute tasks assigned by the master and return the results.

The `masterWorker` requires capabilities for networking, multitier programming, placed flows, and placed values.
The `MasterWorkerNetwork` is a specialized network capability that allows the `Master` to communicate with multiple `Worker`s.
The `Multitier` capability enables the definition of code that runs on different parties.
The `PlacedFlow` capability allows for the creation and manipulation of data flows that are specific to certain locations.
The `PlacedValue` capability manages values that are specific to certain parties.