package io.github.nicolasfara.locix

import placement.Peers.Quantifier.*
import placement.PlacedValue
import network.Network.Network
import Choreography.*
import placement.PlacedValue.*
import network.Network.localAddress
import network.InMemoryNetwork

object Broadcasting:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Multiple[Client] }

  def broadcast(using Network, PlacedValue, Choreography) =
    val serverMessage = on[Server] { "Hello from server" }
    val clientMessage = multicast[Server, Client](serverMessage)
    on[Client]:
      val onClient = clientMessage.take
      println(s"${localAddress} Received message: $onClient")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.{ Await, Future }

    // Create one server and three clients
    val serverNetwork = InMemoryNetwork[Server]("server-address", 1)
    val client1Network = InMemoryNetwork[Client]("client1-address", 2)
    val client2Network = InMemoryNetwork[Client]("client2-address", 3)
    val client3Network = InMemoryNetwork[Client]("client3-address", 4)

    // Connect clients to the server and server to clients
    client1Network.addReachablePeer(serverNetwork)
    client2Network.addReachablePeer(serverNetwork)
    client3Network.addReachablePeer(serverNetwork)
    serverNetwork.addReachablePeer(client1Network)
    serverNetwork.addReachablePeer(client2Network)
    serverNetwork.addReachablePeer(client3Network)

    // Start server and clients
    val serverFuture = Future:
      println("Starting Server")
      given Locix[InMemoryNetwork[Server]] = Locix(serverNetwork)
      PlacedValue.run[Server]:
        Choreography.run[Server](broadcast)

    val clientFutures = List(
      Future {
        println("Starting Client 1")
        given Locix[InMemoryNetwork[Client]] = Locix(client1Network)
        PlacedValue.run[Client] {
          Choreography.run[Client](broadcast)
        }
      },
      Future {
        println("Starting Client 2")
        given Locix[InMemoryNetwork[Client]] = Locix(client2Network)
        PlacedValue.run[Client] {
          Choreography.run[Client](broadcast)
        }
      },
      Future {
        println("Starting Client 3")
        given Locix[InMemoryNetwork[Client]] = Locix(client3Network)
        PlacedValue.run[Client] {
          Choreography.run[Client](broadcast)
        }
      },
    )

    val complete = Future.sequence(serverFuture :: clientFutures)
    Await.result(complete, scala.concurrent.duration.Duration.Inf)
    println("Main program completed")
  end main
end Broadcasting
