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
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.*
import io.github.locix.raise.Raise

object ConsumeItems:
  type A <: { type Tie <: Single[B] }
  type B <: { type Tie <: Single[A] }

  private def consume(remaining: List[Int] on A)(using Network, Placement, Choreography): Unit = Choreography:
    val hasMore: Boolean on A = on[A]:
      take(remaining).nonEmpty
    if broadcast[A, Boolean](hasMore) then
      val headAtA: Int on A = on[A]:
        take(remaining).head
      val tailAtA: List[Int] on A = on[A]:
        take(remaining).iterator.drop(1).toList
      val headAtB = comm[A, B](headAtA)
      on[B]:
        println(s"[B] consumed ${take(headAtB)}")
      consume(tailAtA)
    else
      on[A]:
        println("[A] no more items")
      on[B]:
        println("[B] done")

  def consumeItemsProtocol(using Network, Placement, Choreography) = Choreography:
    val items = on[A]:
      List(1, 2, 3, 4)
    consume(items)

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

    val fa = Future { handleProgramForPeer[A](a)(consumeItemsProtocol) }
    val fb = Future { handleProgramForPeer[B](b)(consumeItemsProtocol) }

    Await.result(Future.sequence(Seq(fa, fb)), duration.Duration.Inf)
end ConsumeItems
