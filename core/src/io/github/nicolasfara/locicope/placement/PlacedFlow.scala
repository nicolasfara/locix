package io.github.nicolasfara.locicope.placement

import scala.util.NotGiven

import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.TiedToSingle
import io.github.nicolasfara.locicope.placement.Peers.TiedToMultiple
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.network.Network.{ reachablePeers, receive, register }
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.placement.PlacementType.{ on, PeerScope, Placement }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import ox.flow.Flow
import scala.annotation.nowarn

object PlacedFlow:
  type PlacedFlow = Locicope[PlacedFlow.Effect]

  class PlacedFlowPeerScope[P <: Peer] extends PeerScope[P]

  inline def flowOn[P <: Peer](using
      pf: PlacedFlow,
      net: Network,
      ng: NotGiven[PeerScope[P]],
  )[Value: Codec](expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P =
    pf.effect.flowOn[P, Value](peer[P])(expression)

  @nowarn inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedFlow, PeerScope[P]) ?=> V): V =
    val effect = effectImpl[P]()
    val handler: Locicope.Handler[PlacedFlow.Effect, V, V] = new Locicope.Handler[PlacedFlow.Effect, V, V]:
      override def handle(program: (Locicope[Effect]) ?=> V): V = program(using new Locicope(effect))
    given PeerScope[P] = PlacedFlowPeerScope[P]
    Locicope.handle(program)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]() = new Effect:
    opaque type Id[V] = V
    override def flowOn[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P =
      given PeerScope[P] = new PlacedFlowPeerScope[P]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Flow)
      val placedFlow = if peer[LocalPeer] <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(peerRepr)(placedFlow, resourceReference)
  end effectImpl

  trait Effect extends Placement:
    /**
     * Create a flow on a specific peer. The flow will be executed on the specified peer and can be accessed remotely by other peers.
     *
     * @param peerRepr
     *   the representation of the peer where the flow should be created
     * @param expression
     *   the expression that creates the flow, executed within the peer's scope
     * @return
     *   a placed flow that can be accessed from other peers
     */
    def flowOn[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P
end PlacedFlow
