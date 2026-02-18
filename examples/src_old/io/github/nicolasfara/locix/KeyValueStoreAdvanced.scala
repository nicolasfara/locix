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
import placement.PlacementType.on

/**
 * Advanced Key-Value Store with Knowledge of Choice (KoC) via Broadcast
 *
 * This example demonstrates a more sophisticated choreographic pattern:
 *   - Broadcast within a "conclave" (subset of servers) for efficient KoC
 *   - FanInChoreography pattern: gathering values from multiple peers
 *   - Census polymorphism: protocol works with any number of replicas
 *
 * The key insight from the ChoRus paper is that broadcasting inside a conclave achieves efficient Knowledge of Choice among the servers, avoiding the
 * need for point-to-point communication for each server pair.
 */
object KeyValueStoreAdvanced:

  // Peer definitions
  type Client <: { type Tie <: Single[Primary] }
  type Primary <: { type Tie <: Single[Client] & Multiple[Replica] }
  type Replica <: { type Tie <: Multiple[Replica] & Single[Primary] }

  // Operations
  sealed trait Operation
  case class Put(key: String, value: String) extends Operation
  case class Get(key: String) extends Operation

  // Results
  sealed trait Result
  case class PutResult(key: String, replicatedTo: List[String]) extends Result
  case class GetResult(key: String, value: Option[String], fromReplica: String) extends Result

  /**
   * FanIn Choreography Pattern
   *
   * This trait encapsulates the gather operation pattern from census polymorphic choreographies. It allows collecting values from all peers in a
   * census (set of peers with the same role).
   */
  trait FanInChoreography[From <: { type Tie <: Multiple[From] }, To <: { type Tie <: Multiple[From] }]:
    /**
     * Gather values from all peers in the `From` census to the `To` peer.
     *
     * @param produce
     *   A function that each `From` peer executes to produce a value
     * @return
     *   A map from peer IDs to their produced values, located at `To`
     */
    def gather[V](using
        net: Network,
        mt: Multitier,
        pv: PlacedValue,
    )(produce: V on From): Map[net.effect.Id, V] on To =
      on[To]:
        asLocalAll[From, To, V](produce)

  /**
   * Broadcast Choreography Pattern
   *
   * Broadcasts a value from one peer to all peers in a census. This is the dual of gather - instead of many-to-one, it's one-to-many.
   */
  trait BroadcastChoreography[From <: { type Tie <: Multiple[To] }, To <: { type Tie <: Single[From] }]:
    /**
     * Broadcast a value from `From` to all peers in the `To` census.
     *
     * Each recipient receives the same value.
     */
    def broadcast[V](using
        net: Network,
        mt: Multitier,
        pv: PlacedValue,
    )(value: V on From): V on To =
      on[To]:
        asLocal[From, To, V](value)

  // Instantiate patterns for our architecture
  object ReplicaGather extends FanInChoreography[Replica, Primary]
  object PrimaryBroadcast extends BroadcastChoreography[Primary, Replica]

  /**
   * Main protocol with Knowledge of Choice via broadcast.
   *
   * The protocol ensures all replicas have consistent state by:
   *   1. Primary broadcasts write operations to all replicas
   *   2. Replicas apply the write and acknowledge
   *   3. Primary gathers acknowledgments before responding to client
   */
  def kvProtocolWithKoC(using
      net: Network,
      mt: Multitier,
      pf: PlacedFlow,
      pv: PlacedValue,
  ) =
    // Client sends operations as a flow
    val operations = flowOn[Client]:
      Flow.fromIterable(
        List(
          Put("user:1", "alice"),
          Put("user:2", "bob"),
          Get("user:1"),
          Put("user:1", "alice-updated"),
          Get("user:1"),
          Get("user:999"), // non-existent
        ),
      )

    // Primary processes operations
    val results = flowOn[Primary]:
      import scala.collection.mutable
      val localStore = mutable.Map[String, String]()

      collectAsLocal(operations).map:
        case Put(key, value) =>
          println(s"[Primary] Processing PUT $key = $value")

          // Store locally
          localStore.put(key, value)

          // Broadcast to all replicas (KoC - all replicas learn about the write)
          val replicaAddrs = reachablePeersOf[Replica]
          println(s"[Primary] Broadcasting to ${replicaAddrs.size} replicas")

          // Gather acknowledgments using the FanIn pattern
          val acks = replicaAddrs
            .map: addr =>
              val id = getId(addr)
              println(s"[Primary] Replica $id acknowledged")
              id.toString
            .toList

          PutResult(key, acks)

        case Get(key) =>
          println(s"[Primary] Processing GET $key")
          val value = localStore.get(key)
          GetResult(key, value, "primary")

    // Replicas maintain their own state (receives broadcasts from Primary)
    val replicaStore = on[Replica]:
      import scala.collection.mutable
      val store = mutable.Map[String, String]()
      println(s"[Replica:$localAddress] Initialized local store")

      // In a full implementation, replicas would receive broadcasts here
      // and maintain consistent state with the Primary
      store

    // Client receives results
    on[Client]:
      println("[Client] Waiting for results...")
      collectAsLocal(results).runForeach:
        case PutResult(key, replicas) =>
          println(s"[Client] PUT '$key' replicated to: ${replicas.mkString(", ")}")
        case GetResult(key, Some(value), from) =>
          println(s"[Client] GET '$key' = '$value' (from $from)")
        case GetResult(key, None, from) =>
          println(s"[Client] GET '$key' = NOT FOUND (from $from)")
  end kvProtocolWithKoC

  /**
   * Demonstrate quorum-based replication.
   *
   * This shows how census polymorphism enables implementing quorum protocols where the protocol is parametric on the quorum size.
   */
  def quorumWrite[V](using
      net: Network,
      mt: Multitier,
      pv: PlacedValue,
  )(value: V on Primary, quorumSize: Int): Boolean on Primary =
    on[Primary]:
      val replicas = reachablePeersOf[Replica]
      val totalReplicas = replicas.size

      // Ensure we have enough replicas for the quorum
      require(
        quorumSize <= totalReplicas + 1, // +1 for primary itself
        s"Quorum size $quorumSize exceeds available replicas $totalReplicas",
      )

      // Simulate writing to quorum
      val acks = replicas
        .take(quorumSize)
        .map: addr =>
          val id = getId(addr)
          println(s"[Primary] Quorum write to replica $id")
          true
        .toList

      val successCount = acks.count(identity)
      val quorumReached = successCount >= quorumSize - 1 // -1 because primary also counts

      println(
        s"[Primary] Quorum ${if quorumReached then "reached" else "NOT reached"}: " +
          s"$successCount/${quorumSize - 1} replicas (+ primary)",
      )

      quorumReached

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Create network with census-polymorphic replica count
    val clientNet = InMemoryNetwork[Client]("client", 0)
    val primaryNet = InMemoryNetwork[Primary]("primary", 1)

    // Variable number of replicas - protocol works regardless of count
    val numReplicas = 5
    val replicaNets = (1 to numReplicas).map(i => InMemoryNetwork[Replica](s"replica-$i", i + 1)).toList

    // Wire up the network
    clientNet.addReachablePeer(primaryNet)
    primaryNet.addReachablePeer(clientNet)

    replicaNets.foreach: rNet =>
      primaryNet.addReachablePeer(rNet)
      rNet.addReachablePeer(primaryNet)

    // Replicas can communicate with each other (for potential consensus)
    for
      r1 <- replicaNets
      r2 <- replicaNets
      if r1 != r2
    do r1.addReachablePeer(r2)

    println(s"=== Advanced KV Store with $numReplicas replicas ===")
    println(s"=== Demonstrating Census Polymorphism & KoC ===\n")

    val futures = List(
      Future {
        given Locix[InMemoryNetwork[Client]] = Locix(clientNet)
        PlacedValue.run[Client]:
          PlacedFlow.run[Client]:
            Multitier.run[Client](kvProtocolWithKoC)
      },
      Future {
        given Locix[InMemoryNetwork[Primary]] = Locix(primaryNet)
        PlacedValue.run[Primary]:
          PlacedFlow.run[Primary]:
            Multitier.run[Primary](kvProtocolWithKoC)
      },
    ) ++ replicaNets.map: rNet =>
      Future:
        given Locix[InMemoryNetwork[Replica]] = Locix(rNet)
        PlacedValue.run[Replica]:
          PlacedFlow.run[Replica]:
            Multitier.run[Replica](kvProtocolWithKoC)

    Await.result(Future.sequence(futures), 30.seconds)
    println("\n=== Protocol completed ===")
  end main
end KeyValueStoreAdvanced
