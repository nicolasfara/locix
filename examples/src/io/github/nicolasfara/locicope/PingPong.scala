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
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.Single

object PingPong:
  type Pinger <: Peer { type Tie <: Single[Ponger] }
  type Ponger <: Peer { type Tie <: Single[Pinger] }

  def pingPongProgram[P <: Peer](using Network, Choreography, PlacedValue) =
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
