package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.*
import Multitier.*
import java.time.LocalDateTime
import io.github.nicolasfara.locix.placement.PlacedFlow
import io.github.nicolasfara.locix.placement.PlacedFlow.*
import ox.flow.Flow

object EmailApplication:
  type Server <: { type Tie <: Single[Client] }
  type Client <: { type Tie <: Single[Server] }

  case class Email(date: LocalDateTime, content: String)

  def emailApp(using Network, Multitier, PlacedValue, PlacedFlow) =
    val word = on[Client] { "Locix" }
    val days = on[Client] { 7 }

    val allEmails = flowOn[Server]:
      Flow.fromValues(
        Email(LocalDateTime.now().minusDays(1), "Welcome to Locix!"),
        Email(LocalDateTime.now().minusDays(5), "Your Locix subscription is active."),
        Email(LocalDateTime.now().minusDays(10), "Don't miss our Locix webinar."),
        Email(LocalDateTime.now().minusDays(15), "Learn more about Locix."),
      )
    val filteredEmails = flowOn[Server]:
      val emails = allEmails.takeFlow
      val w = asLocal(word)
      val d = asLocal(days)
      emails.filter(email => email.content.contains(w) && email.date.isAfter(LocalDateTime.now().minusDays(d)))

    val clientEmails = on[Client]:
      val emails = collectAsLocal[Server, Client, Email](filteredEmails)
      emails.runForeach { email =>
        println(s"[$localAddress] Received email: ${email.content} (Date: ${email.date})")
      }
      println(s"[$localAddress] Email search completed.")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val serverNetwork = io.github.nicolasfara.locix.network.InMemoryNetwork[Server]("server-address", 1)
    val clientNetwork = io.github.nicolasfara.locix.network.InMemoryNetwork[Client]("client-address", 2)

    serverNetwork.addReachablePeer(clientNetwork)
    clientNetwork.addReachablePeer(serverNetwork)

    val serverFuture = scala.concurrent.Future:
      println("Starting Server")
      given Locix[io.github.nicolasfara.locix.network.InMemoryNetwork[Server]] = Locix(serverNetwork)
      PlacedFlow.run[Server]:
        PlacedValue.run[Server]:
          Multitier.run[Server](emailApp)
    val clientFuture = scala.concurrent.Future:
      println("Starting Client")
      given Locix[io.github.nicolasfara.locix.network.InMemoryNetwork[Client]] = Locix(clientNetwork)
      PlacedFlow.run[Client]:
        PlacedValue.run[Client]:
          Multitier.run[Client](emailApp)

    val complete = scala.concurrent.Future.sequence(List(serverFuture, clientFuture))
    scala.concurrent.Await.result(complete, scala.concurrent.duration.Duration.Inf)