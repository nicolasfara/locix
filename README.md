# LociX

> A capability-based Scala framework for typed multiparty distributed programming.

LociX lets you express where code runs, which peers may communicate, and how values move across a distributed topology using a single Scala DSL. The repository includes the core abstractions, an in-memory runtime for local simulation, a broad example suite, and a Nebula-inspired demo that combines the supported paradigms.

## Features

- Typed peer roles and topology constraints with `Single[...]` and `Multiple[...]`.
- Choreographic primitives for explicit communication flows: `comm`, `broadcast`, `multicast`, and `gather`.
- Multitier placement primitives such as `on`, `asLocal`, and `asLocalAll`.
- Collective programming primitives such as `rep`, `nbr`, `branch`, and `mux`.
- Reactive distributed values through `Signal`.
- An in-memory `Network` implementation for runnable local experiments and tests.
- End-to-end examples covering RPC-style interactions, master-worker allocation, aggregate computing, security protocols, and game/protocol case studies.

## Project Layout

- `core`: peer typing, placements, choreography, multitier, collective, and signal primitives.
- `distributed`: runtime support, including the in-memory network used by the examples.
- `examples`: runnable examples and case studies built on top of the core DSL.
- `nebula`: a compact federated-learning-style demo combining choreography, multitier, and collective logic.

## Choreography Example

```scala
import io.github.party.Choreography
import io.github.party.Choreography.*
import io.github.party.network.Network
import io.github.party.peers.Peers.Cardinality.*
import io.github.party.placement.PeerScope.take
import io.github.party.placement.Placement
import io.github.party.placement.PlacementType.on

object PingPong:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def program(using Network, Placement, Choreography) = Choreography:
    val pingOnPinger = on[Pinger]:
      "ping"

    val pingOnPonger = comm[Pinger, Ponger](pingOnPinger)

    val pongOnPonger = on[Ponger]:
      s"${take(pingOnPonger)} -> pong"

    val pongOnPinger = comm[Ponger, Pinger](pongOnPonger)

    on[Pinger]:
      println(take(pongOnPinger))
```

This style is useful when the communication pattern is the main concern and you want it described directly in program order.

## Multitier Example

```scala
import io.github.party.Multitier
import io.github.party.Multitier.*
import io.github.party.network.Network
import io.github.party.network.Network.peerAddress
import io.github.party.network.Network.reachablePeersOf
import io.github.party.peers.Peers.Cardinality.*
import io.github.party.placement.Placement
import io.github.party.placement.PlacementType.on

object MasterWorker:
  type Master <: { type Tie <: Multiple[Worker] }
  type Worker <: { type Tie <: Single[Master] }

  final class Task(input: Int):
    def exec(): Int = input * 2

  def allocateTasks(using n: Network): Map[n.PeerAddress, List[Task]] =
    val inputs = List(1, 2, 3, 4, 5)
    val workers = reachablePeersOf[Worker].toList
    if workers.isEmpty then Map.empty
    else
      val chunkSize = (inputs.size + workers.size - 1) / workers.size
      workers
        .zip(inputs.grouped(chunkSize))
        .toMap
        .map:
          case (worker, tasks) => worker -> tasks.map(new Task(_))

  def program(using Network, Placement, Multitier) = Multitier:
    val allocation = on[Master]:
      allocateTasks

    val workerResults = on[Worker]:
      val localAddress = peerAddress
      asLocal(allocation)
        .filter(_._1 == localAddress)
        .flatMap(_._2)
        .map(_.exec())

    on[Master]:
      val results = asLocalAll(workerResults).values.flatMap(_.toList).toList
      println(s"Master collected results: $results")
```

This is useful when computation is colocated with data or devices, while reads across placements stay explicit and typed.

## Collective Example

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import io.github.party.Collective
import io.github.party.Collective.*
import io.github.party.network.Network
import io.github.party.network.Network.peerAddress
import io.github.party.peers.Peers.Cardinality.*
import io.github.party.placement.PeerScope.take
import io.github.party.placement.Placement
import io.github.party.placement.PlacementType.on

object AggregateCounter:
  type Smartphone <: { type Tie <: Multiple[Smartphone] }

  def program(using Network, Placement, Collective) =
    val counter = Collective[Smartphone](1.second):
      rep(0)(_ + 1)

    on[Smartphone]:
      take(counter).subscribe: value =>
        println(s"Device ${peerAddress} has count: $value")
```

This layer is aimed at neighborhood-based and aggregate computations where each node evolves local state while observing nearby peers.

## Runnable Examples

The [`examples`](./examples/src/io/github/party) module contains small runnable programs such as:

- `PingPong`
- `RemoteFunction`
- `MasterWorker`
- `AggregateCounter`
- `Broadcasting`
- `DistAuth`
- `BuyerSellerShipper`
- `TicTacToe`
- `MergeSort`

The [`nebula`](./nebula/src/io/github/party/nebula) module contains a larger hybrid example where:

- collective programming computes topology awareness and participation eligibility,
- choreography coordinates training rounds and gathers updates,
- multitier programming exposes the final state to a dashboard peer.

## Running The Code

The build uses Mill and Scala 3.8.1 with experimental capabilities enabled.

```bash
./mill core.test
./mill examples.runMain io.github.party.PingPong
./mill examples.runMain io.github.party.MasterWorker
./mill examples.runMain io.github.party.AggregateCounter
./mill nebula.runMain io.github.party.nebula.Nebula
```

For more runnable programs, inspect [`examples/src/io/github/party`](./examples/src/io/github/party).

## License And Conduct

LociX is distributed under the MIT License. Unless stated otherwise, the code in this repository is provided on an "as is" basis, without warranty of any kind. See [`LICENSE`](./LICENSE) for the full license text.

Participation in this project is also governed by the repository [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).
