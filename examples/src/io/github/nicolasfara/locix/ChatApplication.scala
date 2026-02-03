package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.*
import Multitier.*
import network.Network.*
import placement.PlacementType.*
import scala.util.Random

object ChatApplication:
  type Server <: { type Tie <: Multiple[Client] }
  type Client <: { type Tie <: Single[Server] }

  def chat(using Network, Multitier, PlacedValue) =
    val message = on[Client] { Random.alphanumeric.take(10).mkString }
    on[Server]:
        val messages = asLocalAll(message)
        messages.view.foreach { case (clientAddress, msg) =>
          println(s"[Server] Received message from $clientAddress: $msg")
        }

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val serverNetwork = io.github.nicolasfara.locix.network.InMemoryNetwork[Server]("server-address", 1)
    val client1Network = io.github.nicolasfara.locix.network.InMemoryNetwork[Client]("client1-address", 2)
    val client2Network = io.github.nicolasfara.locix.network.InMemoryNetwork[Client]("client2-address", 3)

    serverNetwork.addReachablePeer(client1Network)
    serverNetwork.addReachablePeer(client2Network)
    client1Network.addReachablePeer(serverNetwork)
    client2Network.addReachablePeer(serverNetwork)

    val serverFuture = scala.concurrent.Future:
      println("Starting Server")
      given Locix[io.github.nicolasfara.locix.network.InMemoryNetwork[Server]] = Locix(serverNetwork)
      PlacedValue.run[Server]:
        Multitier.run[Server](chat)

    val client1Future = scala.concurrent.Future:
      println("Starting Client 1")
      given Locix[io.github.nicolasfara.locix.network.InMemoryNetwork[Client]] = Locix(client1Network)
      PlacedValue.run[Client]:
        Multitier.run[Client](chat)

    val client2Future = scala.concurrent.Future:
      println("Starting Client 2")
      given Locix[io.github.nicolasfara.locix.network.InMemoryNetwork[Client]] = Locix(client2Network)
      PlacedValue.run[Client]:
        Multitier.run[Client](chat)

    val allFutures = scala.concurrent.Future.sequence(List(serverFuture, client1Future, client2Future))
    scala.concurrent.Await.result(allFutures, scala.concurrent.duration.Duration.Inf)
