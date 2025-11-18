package io.github.nicolasfara.locix

import scala.concurrent.Await
import scala.concurrent.Future

import Choreography.*
import io.github.nicolasfara.locix.CirceCodec.given
import io.github.nicolasfara.locix.network.InMemoryNetwork
import network.Network
import network.Network.*
import placement.Peers.Peer
import placement.Peers.Quantifier.Single
import placement.Peers.peer
import placement.PlacedValue
import placement.PlacedValue.*
import io.github.nicolasfara.locix.{Choreography, Locix}

object PingPong:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def pingPongProgram(using Network, Choreography, PlacedValue) =
    val ping = on[Pinger]("ping")
    val pingReceived = comm[Pinger, Ponger](ping)
    val pong = on[Ponger]:
      val receivedPing = pingReceived.take
      println(s"[$localAddress] received: $receivedPing")
      "pong"
    val pongReceived = comm[Ponger, Pinger](pong)
    val finalPing = on[Pinger]:
      val receivedPong = pongReceived.take
      println(s"[$localAddress] received: $receivedPong")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val pingerNetwork = InMemoryNetwork(peer[Pinger], "pinger-address", 1)
    val pongerNetwork = InMemoryNetwork(peer[Ponger], "ponger-address", 2)
    pingerNetwork.addReachablePeer(pongerNetwork)
    pongerNetwork.addReachablePeer(pingerNetwork)

    val pingerFuture = Future:
      println("Starting Pinger")
      given Locix[Network.Effect] = Locix[Network.Effect](pingerNetwork)
      PlacedValue.run[Pinger]:
        Choreography.run[Pinger](pingPongProgram)

    val pongerFuture = Future:
      println("Starting Ponger")
      given Locix[Network.Effect] = Locix[Network.Effect](pongerNetwork)
      PlacedValue.run[Ponger]:
        Choreography.run[Ponger](pingPongProgram)

    val complete = Future.sequence(List(pingerFuture, pongerFuture))
    Await.result(complete, scala.concurrent.duration.Duration.Inf)

    println("Main program completed")
  end main
end PingPong
