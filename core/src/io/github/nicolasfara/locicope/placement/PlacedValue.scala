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
import io.github.nicolasfara.locicope.placement.PlacementType.{ on, PeerScope }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import scala.annotation.nowarn

object PlacedValue:
  type PlacedValue = Locicope[PlacedValue.Effect]

  class PlacedValuePeerScope[P <: Peer] extends PeerScope[P]
  class PlacedLabel

  inline def on[P <: Peer](using
      pv: PlacedValue,
      net: Network,
      ng: NotGiven[PlacedLabel],
  )[Value: Codec](expression: PeerScope[P] ?=> Value): Value on P =
    pv.effect.on[P, Value](peer[P])(expression)

  def take[P <: Peer](using pv: PlacedValue, net: Network, scope: PeerScope[P])[V](value: V on P): V =
    pv.effect.take(value)

  extension [Remote <: Peer, Value: Codec](using pl: PlacedValue)(placedValue: Value on Remote)
    def take(using PeerScope[Remote]): Value =
      pl.effect.take(placedValue)

  @nowarn inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedValue, PeerScope[P]) ?=> V): V =
    val peerRepr = peer[P]
    val effect = effectImpl[P]()
    val handler = new Locicope.Handler[PlacedValue.Effect, V, V]:
      override def handle(program: (Locicope[Effect]) ?=> V): V = program(using Locicope(effect))
    given PeerScope[P] = PlacedValuePeerScope[P]
    Locicope.handle(program)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]() = new Effect:
    opaque type Id[V] = V
    override def on[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: (PeerScope[P]) ?=> Value): Value on P =
      given PeerScope[P] = new PlacedValuePeerScope[P]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Value)
      val placedValue: Option[Id[Value]] = if peer[LocalPeer] <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(peerRepr)(placedValue, resourceReference)
    override def take[P <: Peer](using PeerScope[P])[V](value: V on P): V =
      val PlacementType.Placed.Local[V @unchecked, P @unchecked](localValue, _) = value.runtimeChecked
      localValue

  trait Effect extends PlacementType.Placement:
    /**
     * Execute the expression in the context of the specified peer, returning a placed value. If the current execution peer is the same as the
     * specified peer, the value is computed and stored in the network. If the current execution peer is different from the specified peer, a
     * placeholder for the value is created.
     *
     * @param peerRepr
     *   the peer representation where to place the value.
     * @param expression
     *   the expression producing the value to be placed.
     * @return
     *   a placed value representing the value on the specified peer.
     */
    def on[P <: Peer, Value: Codec](using Network, NotGiven[PeerScope[P]])(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Value): Value on P

    def take[P <: Peer](using PeerScope[P])[V](value: V on P): V
  end Effect
end PlacedValue
