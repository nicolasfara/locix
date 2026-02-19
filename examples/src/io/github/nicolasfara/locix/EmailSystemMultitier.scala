package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.handlers.ChoreographyHandler
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await
import io.github.nicolasfara.locix.EmailSystemUtils.createServerDB
import io.github.nicolasfara.locix.EmailSystemUtils.ClientConfig
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.EmailSystemUtils.createClientDB
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.EmailSystemUtils.Attachment
import io.github.nicolasfara.locix.handlers.MultitierHandler

object EmailSystemMultitier:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Single[Client] }

  def emailSyncProtocol(config: ClientConfig)(using Network, Placement, Multitier) = Multitier:
    val emailsOnServer = on[Server]:
      val ts = 0L
      println(s"[Server] Starting email sync protocol with timestamp $ts")
      createServerDB().since(config.userId, ts)
    on[Client]:
      val emails = asLocal(emailsOnServer)
      println(s"[Client] Received ${emails.size} emails from server")
      val db = createClientDB()
      db.update(emails)
    val attachmentsOnServer = on[Server]:
      if config.isOnFlatRate then
        val emails = take(emailsOnServer)
        val emailIds = emails.map(_.id)
        println(s"[Server] Fetching attachments for ${emailIds.size} emails (client on flat rate)")
        emailIds.flatMap(createServerDB().getAttachments)
      else
        println(s"[Server] Skipping attachments (client not on flat rate)")
        List.empty[Attachment]
    on[Client]:
      val attachments = asLocal(attachmentsOnServer)
      if attachments.nonEmpty then createClientDB().updateAttachments(attachments)
      println(s"[Client] Email synchronization completed")
      ()
  end emailSyncProtocol

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Multitier) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Multitier = MultitierHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running Email System multitier...")
    val userId = if args.length > 0 then args(0) else "user1"
    val isOnFlatRate = if args.length > 1 then args(1).toBoolean else true
    val config = ClientConfig(userId, isOnFlatRate)

    val broker = InMemoryNetwork.broker()
    val pingerNetwork = InMemoryNetwork[Client]("client", broker)
    val pongerNetwork = InMemoryNetwork[Server]("server", broker)

    val pingerFuture = Future { handleProgramForPeer[Client](pingerNetwork)(emailSyncProtocol(config)) }
    val pongerFuture = Future { handleProgramForPeer[Server](pongerNetwork)(emailSyncProtocol(config)) }

    // Wait for both peers to finish
    val combinedFuture = Future.sequence(Seq(pingerFuture, pongerFuture))
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
