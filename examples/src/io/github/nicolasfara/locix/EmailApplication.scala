package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import java.time.LocalDateTime
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.network.Network.peerAddress
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.peers.Peers.*
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.handlers.*
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import scala.concurrent.*

object EmailApplication:
  type Server <: { type Tie <: Single[Client] }
  type Client <: { type Tie <: Single[Server] }

  case class Email(date: LocalDateTime, content: String)

  def emailApp(using Network, Placement, Multitier) = Multitier:
    val filters = on[Client] { ("Locix", 7) }
    val allEmails = on[Server]:
      Seq(
        Email(LocalDateTime.now().minusDays(1), "Welcome to Locix!"),
        Email(LocalDateTime.now().minusDays(5), "Your Locix subscription is active."),
        Email(LocalDateTime.now().minusDays(10), "Don't miss our Locix webinar."),
        Email(LocalDateTime.now().minusDays(15), "Learn more about Locix."),
      )
    val filteredEmails = on[Server]:
      val (words, days) = asLocal(filters)
      take(allEmails).filter(email => email.content.contains(words) && email.date.isAfter(LocalDateTime.now().minusDays(days)))
    val clientEmails = on[Client]:
      val emails = asLocal(filteredEmails)
      emails.foreach { email =>
        println(s"[$peerAddress] Received email: ${email.content} (Date: ${email.date})")
      }
      println(s"[$peerAddress] Email search completed.")

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Multitier) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Multitier = MultitierHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val broker = InMemoryNetwork.broker()
    val serverNetwork = InMemoryNetwork[Server]("server-address", broker)
    val clientNetwork = InMemoryNetwork[Client]("client-address", broker)

    val serverFuture = Future { handleProgramForPeer[Server](serverNetwork)(emailApp) }
    val clientFuture = Future { handleProgramForPeer[Client](clientNetwork)(emailApp) }

    val results = Future.sequence(Seq(serverFuture, clientFuture))
    Await.result(results, scala.concurrent.duration.Duration.Inf)