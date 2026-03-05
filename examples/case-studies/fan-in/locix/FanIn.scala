package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

import io.github.nicolasfara.locix.Choreography
import io.github.nicolasfara.locix.Choreography.*
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import io.github.nicolasfara.locix.handlers.ChoreographyHandler
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.PeerScope.take
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.raise.Raise

/**
 * Fan-In choreography.
 *
 * Ported from the ChoRus (Rust) fanin example.
 *
 * Bob and Carol each create a greeting message and send it to Alice. Alice collects both messages and prints them.
 */
object FanIn:

  // ──────────────────────────────────────────────────────────────────────────
  // Peer definitions
  // ──────────────────────────────────────────────────────────────────────────

  type Alice <: { type Tie <: Single[Bob] & Single[Carol] }
  type Bob <: { type Tie <: Single[Alice] }
  type Carol <: { type Tie <: Single[Alice] }

  // ──────────────────────────────────────────────────────────────────────────
  // Choreography
  // ──────────────────────────────────────────────────────────────────────────

  def fanInProtocol(using Network, Placement, Choreography) = Choreography:

    // Bob creates a greeting for Alice
    val msgFromBob: String on Bob = on[Bob]:
      val msg = "Bob says hi to Alice"
      println(s"[Bob] Sending: $msg")
      msg

    // Bob sends the message to Alice
    val bobMsgAtAlice = comm[Bob, Alice](msgFromBob)

    // Carol creates a greeting for Alice
    val msgFromCarol: String on Carol = on[Carol]:
      val msg = "Carol says hi to Alice"
      println(s"[Carol] Sending: $msg")
      msg

    // Carol sends the message to Alice
    val carolMsgAtAlice = comm[Carol, Alice](msgFromCarol)

    // Alice collects and prints both messages
    on[Alice]:
      val fromBob = take(bobMsgAtAlice)
      val fromCarol = take(carolMsgAtAlice)
      println(s"[Alice] Received: \"$fromBob\" from Bob and \"$fromCarol\" from Carol")

  // ──────────────────────────────────────────────────────────────────────────
  // Peer runner
  // ──────────────────────────────────────────────────────────────────────────

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](
      program: (Network, PlacementType, Choreography) ?=> V,
  ): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Choreography = ChoreographyHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running FanIn choreography...")
    val broker = InMemoryNetwork.broker()
    val aliceNet = InMemoryNetwork[Alice]("alice", broker)
    val bobNet = InMemoryNetwork[Bob]("bob", broker)
    val carolNet = InMemoryNetwork[Carol]("carol", broker)

    val f1 = Future { handleProgramForPeer[Alice](aliceNet)(fanInProtocol) }
    val f2 = Future { handleProgramForPeer[Bob](bobNet)(fanInProtocol) }
    val f3 = Future { handleProgramForPeer[Carol](carolNet)(fanInProtocol) }

    Await.result(
      Future.sequence(Seq(f1, f2, f3)),
      scala.concurrent.duration.Duration.Inf,
    )
    println("FanIn done.")
end FanIn
