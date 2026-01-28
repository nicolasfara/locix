package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.duration.*

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{ Choreography, Locix }

import Choreography.*
import network.Network.*
import placement.Peers.Quantifier.*
import placement.Peers.peer
import placement.PlacedValue
import placement.PlacedValue.*
import placement.PlacementType.on

object EmailSystemChoreo:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Single[Client] }

  type UserId = String

  trait MainServerDB:
    def getAttachments(emailId: Long): List[Attachment]
    def since(id: UserId, ts: Long): List[Email]

  trait MainDB:
    def updateAttachments(attachments: List[Attachment]): Unit
    def update(emails: List[Email]): Unit
    def extractIds(emails: List[Email]): List[Long] =
      emails.map(_.id)
    def lastCheckout: Long

  case class Email(id: Long, subject: String, body: String, timestamp: Long, attachments: List[Attachment])
  case class Attachment(filename: String, data: Array[Byte])

  case class ClientConfig(userId: UserId, isOnFlatRate: Boolean)

  // Mock implementations for demonstration
  def createServerDB(): MainServerDB = new MainServerDB:
    private val emails = Map(
      "user1" -> List(
        Email(1L, "Welcome", "Welcome to our service", 1000L, List.empty),
        Email(2L, "Update", "System update available", 2000L, List.empty),
        Email(3L, "Newsletter", "Monthly newsletter", 3000L, List.empty),
      ),
    )
    private val attachments = Map(
      1L -> List(Attachment("welcome.pdf", Array[Byte](1, 2, 3))),
      2L -> List(Attachment("update.zip", Array[Byte](4, 5, 6))),
      3L -> List(Attachment("newsletter.pdf", Array[Byte](7, 8, 9))),
    )

    def getAttachments(emailId: Long): List[Attachment] =
      println(s"[Server] Fetching attachments for email $emailId")
      attachments.getOrElse(emailId, List.empty)

    def since(id: UserId, ts: Long): List[Email] =
      println(s"[Server] Fetching emails for user $id since timestamp $ts")
      emails.getOrElse(id, List.empty).filter(_.timestamp > ts)

  def createClientDB(): MainDB = new MainDB:
    val lastCheckout: Long = 0L

    def updateAttachments(attachments: List[Attachment]): Unit =
      println(s"[Client] Updating ${attachments.size} attachments")

    def update(emails: List[Email]): Unit =
      println(s"[Client] Updating ${emails.size} emails")

  /**
   * Email synchronization protocol
   *
   * This demonstrates a multi-tier choreography where a client synchronizes emails from a server, with context-aware attachment fetching based on
   * network conditions.
   */
  def emailSyncProtocol(config: ClientConfig)(using Network, PlacedValue, Choreography) =
    // Get emails from server
    val emailsOnServer: List[Email] on Server = on[Server]:
      val ts = 0L
      println(s"[Server] Fetching emails for user ${config.userId}")
      createServerDB().since(config.userId, ts)

    val emailsOnClient = comm[Server, Client](emailsOnServer)

    // Update emails on client
    on[Client]:
      val emails = emailsOnClient.take
      val db = createClientDB()
      db.update(emails)

    // Fetch attachments from server if client is on flat rate
    val attachmentsOnServer: List[Attachment] on Server = on[Server]:
      if config.isOnFlatRate then
        val emails = emailsOnServer.take
        val emailIds = emails.map(_.id)
        println(s"[Server] Fetching attachments for ${emailIds.size} emails (client on flat rate)")
        emailIds.flatMap(createServerDB().getAttachments)
      else
        println(s"[Server] Skipping attachments (client not on flat rate)")
        List.empty[Attachment]

    val attachmentsOnClient = comm[Server, Client](attachmentsOnServer)

    // Update attachments on client if any were fetched
    on[Client]:
      val attachments = attachmentsOnClient.take
      if attachments.nonEmpty then createClientDB().updateAttachments(attachments)
      println(s"[Client] Email synchronization completed")
      ()
  end emailSyncProtocol

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Read configuration from arguments or use defaults
    val userId = if args.length > 0 then args(0) else "user1"
    val isOnFlatRate = if args.length > 1 then args(1).toBoolean else true
    val config = ClientConfig(userId, isOnFlatRate)

    println(s"Configuration: userId=$userId, isOnFlatRate=$isOnFlatRate")
    println()

    // Create network nodes for each participant
    val clientNetwork = InMemoryNetwork[Client]("client-address", 1)
    val serverNetwork = InMemoryNetwork[Server]("server-address", 2)

    // Set up bidirectional connectivity between Client and Server
    clientNetwork.addReachablePeer(serverNetwork)
    serverNetwork.addReachablePeer(clientNetwork)

    println("=== Email System Protocol ===\n")

    // Run each participant concurrently
    val clientFuture = Future:
      println("Starting Client")
      given Locix[InMemoryNetwork[Client]] = Locix(clientNetwork)
      PlacedValue.run[Client]:
        Choreography.run[Client](emailSyncProtocol(config))

    val serverFuture = Future:
      println("Starting Server")
      given Locix[InMemoryNetwork[Server]] = Locix(serverNetwork)
      PlacedValue.run[Server]:
        Choreography.run[Server](emailSyncProtocol(config))

    val complete = Future.sequence(List(clientFuture, serverFuture))
    Await.result(complete, 10.seconds)

    println("\n=== Email System Protocol Completed ===")
  end main
end EmailSystemChoreo
