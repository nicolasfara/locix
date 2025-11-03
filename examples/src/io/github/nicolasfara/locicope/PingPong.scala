package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.Choreography.Choreography
import io.github.nicolasfara.locicope.Choreography
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.Quantifier
import io.github.nicolasfara.locicope.Choreography.comm
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.CirceCodec.given
import io.github.nicolasfara.locicope.placement.PlacedValue.take

object PingPong:
  type Pinger <: Peer { type Tie <: Quantifier.Single[Ponger] }
  type Ponger <: Peer { type Tie <: Quantifier.Single[Pinger] }

  def pingPongProgram[P <: Peer](using Network, Choreography, PlacedValue) =
    val ping = on[Pinger]("ping")
    val pingReceived = comm[Pinger, Ponger](ping)
    val pong = on[Ponger]:
      val receivedPing = take(pingReceived)
      println(s"Ponger received: $receivedPing")
      s"$receivedPing pong"
    val pongReceived = comm[Ponger, Pinger](pong)
    val finalPing = on[Pinger]:
      val receivedPong = take(pongReceived)
      println(s"Pinger received: $receivedPong")
