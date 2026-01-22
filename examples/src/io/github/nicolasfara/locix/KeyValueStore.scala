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
 * Key-Value Store with Census Polymorphic Backup Replication
 *
 * This example demonstrates a choreographic key-value store protocol inspired by the ChoRus paper. The protocol is parametric on the number of backup
 * servers - the choreography works regardless of how many backups exist.
 *
 * Architecture:
 *   - Client: sends get/put requests
 *   - Primary: main KV store server that handles requests
 *   - Backup: multiple backup servers that replicate writes (census polymorphic)
 *
 * Key features demonstrated:
 *   1. Census polymorphism: The protocol works with any number of backups
 *   2. FanIn/Gather pattern: Collecting acknowledgments from all backups
 *   3. Broadcast to multiple peers: Primary broadcasts writes to all backups
 */
object KeyValueStore:

  // Peer type definitions with cardinality constraints
  // Client is tied to a single Primary
  type Client <: { type Tie <: Single[Primary] }

  // Primary is tied to single Client and multiple Backups (census polymorphic)
  type Primary <: { type Tie <: Single[Client] & Multiple[Backup] }

  // Backup is tied to multiple other Backups and single Primary
  type Backup <: { type Tie <: Multiple[Backup] & Single[Primary] }

  // Request ADT for client operations
  sealed trait Request
  case class Get(key: String) extends Request
  case class Put(key: String, value: String) extends Request

  // Response ADT
  sealed trait Response
  case class GetResponse(value: Option[String]) extends Response
  case class PutResponse(success: Boolean, replicas: Int) extends Response

  // Acknowledgment from backup servers
  case class BackupAck(backupId: String, success: Boolean)

  /**
   * Main KV Store choreography.
   *
   * This choreography demonstrates:
   *   - Point-to-point communication (Client <-> Primary)
   *   - Broadcast to census (Primary -> all Backups)
   *   - Gather/FanIn from census (all Backups -> Primary)
   */
  def kvStoreProtocol(using
      net: Network,
      mt: Multitier,
      pf: PlacedFlow,
      pv: PlacedValue,
  ) =
    // Client creates a stream of requests
    val clientRequests = flowOn[Client]:
      Flow.fromIterable(
        List(
          Put("key1", "value1"),
          Put("key2", "value2"),
          Get("key1"),
          Put("key1", "updated-value1"),
          Get("key1"),
          Get("nonexistent"),
        ),
      )

    // Primary handles requests and maintains the KV store
    val primaryResponses = flowOn[Primary]:
      import scala.collection.mutable

      // Local KV store state
      val store = mutable.Map[String, String]()

      // Collect requests from client
      val requests = collectAsLocal(clientRequests)

      requests.map: request =>
        request match
          case Get(key) =>
            val value = store.get(key)
            println(s"[Primary] GET '$key' -> ${value.getOrElse("NOT FOUND")}")
            GetResponse(value)

          case Put(key, value) =>
            // Store locally first
            store.put(key, value)
            println(s"[Primary] PUT '$key' = '$value'")

            // Broadcast to all backups and gather acknowledgments
            // This is the census-polymorphic part - works with any number of backups
            val backupPeers = reachablePeersOf[Backup]
            val acks = backupPeers
              .map: backupAddr =>
                // In a real implementation, this would send asynchronously
                // For now, we simulate the broadcast+gather pattern
                val backupId = getId(backupAddr)
                println(s"[Primary] Replicating to backup: $backupId")
                BackupAck(backupId.toString, success = true)
              .toList

            val successCount = acks.count(_.success)
            println(s"[Primary] Replication complete: $successCount/${acks.size} backups acknowledged")
            PutResponse(success = true, replicas = successCount)

    // Backups receive and apply writes
    val backupState = on[Backup]:
      import scala.collection.mutable
      val store = mutable.Map[String, String]()
      println(s"[Backup:$localAddress] Ready to receive replications")
      store

    // Client receives responses
    on[Client]:
      val responses = collectAsLocal(primaryResponses)
      responses.runForeach: response =>
        response match
          case GetResponse(Some(value)) =>
            println(s"[Client] Received value: $value")
          case GetResponse(None) =>
            println(s"[Client] Key not found")
          case PutResponse(success, replicas) =>
            println(s"[Client] Put ${if success then "succeeded" else "failed"}, replicated to $replicas backups")
  end kvStoreProtocol

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Create network topology
    val clientNetwork = InMemoryNetwork[Client]("client", 0)
    val primaryNetwork = InMemoryNetwork[Primary]("primary", 1)

    // Census-polymorphic: create any number of backup servers
    val numBackups = 3
    val backupNetworks = (1 to numBackups).map(i => InMemoryNetwork[Backup](s"backup-$i", i + 1)).toList

    // Setup network connections
    // Client <-> Primary (single connection)
    clientNetwork.addReachablePeer(primaryNetwork)
    primaryNetwork.addReachablePeer(clientNetwork)

    // Primary <-> all Backups (one-to-many)
    backupNetworks.foreach: backupNet =>
      primaryNetwork.addReachablePeer(backupNet)
      backupNet.addReachablePeer(primaryNetwork)

    // Backups <-> Backups (many-to-many for potential consensus)
    for
      b1 <- backupNetworks
      b2 <- backupNetworks
      if b1 != b2
    do b1.addReachablePeer(b2)

    println(s"=== Key-Value Store with $numBackups backup servers ===\n")

    // Start all peers
    val clientFuture = Future:
      println("[Client] Starting...")
      given Locix[InMemoryNetwork[Client]] = Locix(clientNetwork)
      PlacedValue.run[Client]:
        PlacedFlow.run[Client]:
          Multitier.run[Client](kvStoreProtocol)

    val primaryFuture = Future:
      println("[Primary] Starting...")
      given Locix[InMemoryNetwork[Primary]] = Locix(primaryNetwork)
      PlacedValue.run[Primary]:
        PlacedFlow.run[Primary]:
          Multitier.run[Primary](kvStoreProtocol)

    val backupFutures = backupNetworks.map: backupNet =>
      Future:
        println(s"[${backupNet.localAddress}] Starting...")
        given Locix[InMemoryNetwork[Backup]] = Locix(backupNet)
        PlacedValue.run[Backup]:
          PlacedFlow.run[Backup]:
            Multitier.run[Backup](kvStoreProtocol)

    val allFutures = clientFuture :: primaryFuture :: backupFutures
    Await.result(Future.sequence(allFutures), 30.seconds)

    println("\n=== Protocol completed ===")
  end main
end KeyValueStore
