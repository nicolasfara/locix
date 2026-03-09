package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

import io.github.locix.Choreography.*
import io.github.locix.EmailSystemUtils.*
import io.github.locix.distributed.InMemoryNetwork
import io.github.locix.handlers.*
import io.github.locix.network.Network
import io.github.locix.network.NetworkError
import io.github.locix.peers.Peers.*
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.placement.*
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.PlacementType.on
import io.github.locix.raise.Raise

object EmailSystemChoreo:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Single[Client] }

  def emailSyncProtocol(config: ClientConfig)(using Network, Placement, Choreography) = Choreography:
    val emailsOnServer = on[Server]:
      val ts = 0L
      println(s"[Server] Starting email sync protocol with timestamp $ts")
      createServerDB().since(config.userId, ts)
    val emailsOnClient = comm[Server, Client](emailsOnServer)
    on[Client]:
      val emails = take(emailsOnClient)
      println(s"[Client] Received ${emails.size} emails from server")
      val db = createClientDB()
      db.update(emails)
    val attachmentsOnServer = on[Server]:
      if config.isOnFlatRate then
        val emailIds = take(emailsOnServer).map(_.id)
        println(s"[Server] Fetching attachments for ${emailIds.size} emails (client on flat rate)")
        emailIds.flatMap(createServerDB().getAttachments)
      else
        println("[Server] Skipping attachments (client not on flat rate)")
        List.empty[Attachment]
    val attachmentsOnClient = comm[Server, Client](attachmentsOnServer)
    on[Client]:
      val attachments = take(attachmentsOnClient)
      if attachments.nonEmpty then createClientDB().updateAttachments(attachments)
      println("[Client] Email synchronization completed")
  end emailSyncProtocol

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Choreography) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Choreography = ChoreographyHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running Email System choreography...")
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
end EmailSystemChoreo
