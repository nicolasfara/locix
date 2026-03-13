package io.github.locix

import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

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
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.*
import io.github.locix.raise.Raise

object HelloRoles:
  type A <: { type Tie <: Single[B] }
  type B <: { type Tie <: Single[A] }

  def helloRoles(using Network, Placement, Choreography) = Choreography:
    on[A]:
      println("Hello from A")
    on[B]:
      println("Hello from B")

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](
      program: (Network, PlacementType, Choreography) ?=> V,
  ): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given PlacementType = PlacementTypeHandler.handler[P]
    given Choreography = ChoreographyHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    val broker = InMemoryNetwork.broker()
    val a = InMemoryNetwork[A]("a", broker)
    val b = InMemoryNetwork[B]("b", broker)

    val fa = Future { handleProgramForPeer[A](a)(helloRoles) }
    val fb = Future { handleProgramForPeer[B](b)(helloRoles) }

    Await.result(Future.sequence(Seq(fa, fb)), duration.Duration.Inf)
end HelloRoles
