package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.Choreography.Choreography
import io.github.nicolasfara.locicope.Choreography
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.Quantifier
import io.github.nicolasfara.locicope.Choreography.comm
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.CirceCodec.given
import io.github.nicolasfara.locicope.placement.PlacedValue.take
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.Single
import io.github.nicolasfara.locicope.network.InMemoryNetwork
import io.github.nicolasfara.locicope.placement.Peers.peer
import scala.concurrent.Future
import scala.concurrent.Await

object PingPong:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def pingPongProgram(using Network, Choreography, PlacedValue) =
    val ping = on[Pinger]("ping")
    val pingReceived = comm[Pinger, Ponger](ping)
    val pong = on[Ponger]:
      val receivedPing = pingReceived.take
      println(s"Ponger received: $receivedPing")
      s"$receivedPing pong"
    val pongReceived = comm[Ponger, Pinger](pong)
    val finalPing = on[Pinger]:
      val receivedPong = pongReceived.take
      println(s"Pinger received: $receivedPong")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val pingerNetwork = InMemoryNetwork(peer[Pinger], "pinger-address", 1)
    val pongerNetwork = InMemoryNetwork(peer[Ponger], "ponger-address", 2)
    pingerNetwork.addReachablePeer(pongerNetwork)
    pongerNetwork.addReachablePeer(pingerNetwork)

    val pingerFuture = Future:
      println("Starting Pinger")
      given Locicope[Network.Effect] = Locicope[Network.Effect](pingerNetwork)
      PlacedValue.run[Pinger]:
        Choreography.run[Pinger](pingPongProgram)

    val pongerFuture = Future:
      println("Starting Ponger")
      given Locicope[Network.Effect] = Locicope[Network.Effect](pongerNetwork)
      PlacedValue.run[Ponger]:
        Choreography.run[Ponger](pingPongProgram)

    val complete = Future.sequence(List(pingerFuture, pongerFuture))
    Await.result(complete, scala.concurrent.duration.Duration.Inf)

    println("Main program completed")
  end main
end PingPong
