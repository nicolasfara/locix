package io.github.nicolasfara.locix.placement

import scala.annotation.nowarn
import scala.util.NotGiven

import io.github.nicolasfara.locix.Locix
import io.github.nicolasfara.locix.macros.ASTHashing.hashBody
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.network.NetworkResource.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.serialization.Codec
import ox.flow.Flow

object PlacedFlow:
  type PlacedFlow = Locix[PlacedFlow.Effect]

  inline def flowOn[P <: Peer](using
      pf: PlacedFlow,
      net: Network,
      ng: NotGiven[PeerScope[P]],
  )[Value: Codec](expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P =
    pf.effect.flowOn[P, Value](peer[P])(expression)

  extension [Remote <: Peer, Value: Codec](using pf: PlacedFlow)(placedFlow: Flow[Value] on Remote)
    def take(using PeerScope[Remote]): Flow[Value] =
      pf.effect.take(placedFlow)

  @nowarn inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedFlow, PeerScope[P]) ?=> V): V =
    val effect = effectImpl[P]()
    val handler: Locix.Handler[PlacedFlow.Effect, V, V] = new Locix.Handler[PlacedFlow.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using new Locix(effect))
    given PeerScope[P] = PeerScope[P]()
    Locix.handle(program)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]() = new Effect:
    override def take[P <: Peer](using PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
      val PlacementType.Placed.Local[Flow[V] @unchecked, P @unchecked](localValue, _) = value.runtimeChecked
      localValue

    opaque type Id[V] = V
    override def flowOn[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P =
      given PeerScope[P] = PeerScope[P]()
      given PlacedLabel = new PlacedLabel
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
    )(peerRepr: PeerRepr)(expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P

    def take[P <: Peer](using PeerScope[P])[V](value: Flow[V] on P): Flow[V]
end PlacedFlow
