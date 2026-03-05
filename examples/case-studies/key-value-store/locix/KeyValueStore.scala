package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.signal.Signal.signalBuilder

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.network.Network.reachablePeersOf
import io.github.nicolasfara.locix.Choreography.broadcast
import io.github.nicolasfara.locix.Choreography.multicast
import io.github.nicolasfara.locix.Choreography.comm
import java.util.UUID
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.handlers.ChoreographyHandler
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import scala.concurrent.Future
import scala.concurrent.Await
import io.github.nicolasfara.locix.network.Network.peerAddress

object KeyValueStore:
  type Client <: { type Tie <: Single[Primary] }
  type Primary <: { type Tie <: Single[Client] & Multiple[Backup] }
  type Backup <: { type Tie <: Multiple[Backup] & Single[Primary] }

  private val store: mutable.Map[String, String] = mutable.Map.empty

  // Request ADT for client operations
  enum Request:
    case Get(key: String)
    case Put(key: String, value: String)

  // Response ADT
  type Response = Option[String]

  // Acknowledgment from backup servers
  final case class BackupAck(backupId: String, success: Boolean)

  private def handleRequest(request: Request): Response = request match
    case Request.Get(key) => store.get(key)
    case Request.Put(key, value) => store += (key -> value); None

  def kvStoreProtocol(request: Request on Client)(using Network, Placement, Choreography): Response on Client = Choreography:
    val requestOnPrimary = comm[Client, Primary](request)
    val req = broadcast(requestOnPrimary)
    req match
      case Request.Put(key, value) =>
        val requestOnBackup = multicast[Primary, Backup](requestOnPrimary)
        on[Backup]:
          println(s"[Backup $peerAddress] Received update request: $key -> $value")
          handleRequest(take(requestOnBackup))
      case Request.Get(key) => ()
    val response = on[Primary]:
      println(s"[Primary $peerAddress] Handling request: $req")
      handleRequest(take(requestOnPrimary))
    comm[Primary, Client](response)

  def kvs(using Network, Placement, Choreography) = Choreography:
    val operations =
      List(Request.Put("foo", "bar"), Request.Get("foo"), Request.Put("hello", "world"), Request.Get("hello"), Request.Get("nonexistent"))
    operations.foreach: op =>
      val request = on[Client](op)
      val response = kvStoreProtocol(request)
      on[Client] { println(s"[Client ${peerAddress}] Received response: ${take(response)} for request: ${take(request)}") }

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Choreography) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Choreography = ChoreographyHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running KeyValueStore choreography...")
    val broker = InMemoryNetwork.broker()
    val clientNetwork = InMemoryNetwork[Client]("client", broker)
    val primaryNetwork = InMemoryNetwork[Primary]("primary", broker)
    val backup1Network = InMemoryNetwork[Backup]("backup1", broker)
    val backup2Network = InMemoryNetwork[Backup]("backup2", broker)

    val clientFuture = Future { handleProgramForPeer[Client](clientNetwork)(kvs) }
    val primaryFuture = Future { handleProgramForPeer[Primary](primaryNetwork)(kvs) }
    val backup1Future = Future { handleProgramForPeer[Backup](backup1Network)(kvs) }
    val backup2Future = Future { handleProgramForPeer[Backup](backup2Network)(kvs) }

    // Wait for both peers to finish
    val combinedFuture = Future.sequence(Seq(clientFuture, primaryFuture, backup1Future, backup2Future))
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
end KeyValueStore
