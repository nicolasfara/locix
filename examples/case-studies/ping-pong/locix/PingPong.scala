package io.github.nicolasfara.locix

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.github.locix.Choreography
import io.github.locix.Choreography.*
import io.github.locix.distributed.InMemoryNetwork
import io.github.locix.handlers.ChoreographyHandler
import io.github.locix.handlers.PlacementTypeHandler
import io.github.locix.network.Network
import io.github.locix.network.NetworkError
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.peers.Peers.Peer
import io.github.locix.peers.Peers.PeerTag
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.*
import io.github.locix.raise.Raise

object PingPong:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def pingPong(using Network, Placement, Choreography) = Choreography:
    var c = 0
    while c < 5 do
      println(s"--- [${summon[Network].peerAddress}] PingPong iteration $c ---")
      val onPinger = on[Pinger]:
        val message = s"Ping $c"
        println(s"Pinger sending message: $message")
        message
      val messageOnPonger = comm[Pinger, Ponger](onPinger)
      on[Ponger]:
        val msg = take(messageOnPonger)
        println(s"Pong received message: $msg")
      c += 1
      Thread.sleep(500) // Simulate some delay

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Choreography) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Choreography = ChoreographyHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running PingPong choreography...")
    val broker = InMemoryNetwork.broker()
    val pingerNetwork = InMemoryNetwork[Pinger]("pinger", broker)
    val pongerNetwork = InMemoryNetwork[Ponger]("ponger", broker)

    val pingerFuture = Future { handleProgramForPeer[Pinger](pingerNetwork)(pingPong) }
    val pongerFuture = Future { handleProgramForPeer[Ponger](pongerNetwork)(pingPong) }

    // Wait for both peers to finish
    val combinedFuture = Future.sequence(Seq(pingerFuture, pongerFuture))
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
end PingPong
