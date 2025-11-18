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

object PlacedValue:
  type PlacedValue = Locix[PlacedValue.Effect]

  /**
   * Execute the expression in the context of the specified peer, returning a placed value.
   *
   * If the current execution peer is the same as the specified peer, the value is computed and stored in the network. If the current execution peer
   * is different from the specified peer, a placeholder for the value is created.
   *
   * @param peerRepr
   *   the peer representation where to place the value.
   * @param expression
   *   the expression producing the value to be placed.
   * @return
   *   a placed value representing the value on the specified peer.
   */
  inline def on[P <: Peer](using
      pv: PlacedValue,
      net: Network,
      ng: NotGiven[PlacedLabel],
  )[Value: Codec](expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P =
    pv.effect.on[P, Value](peer[P])(expression)

  def take[P <: Peer](using pv: PlacedValue, net: Network, scope: PeerScope[P])[V](value: V on P): V =
    pv.effect.take(value)

  extension [Remote <: Peer, Value: Codec](using pl: PlacedValue)(placedValue: Value on Remote)
    def take(using PeerScope[Remote]): Value =
      pl.effect.take(placedValue)

  @nowarn inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedValue, PeerScope[P]) ?=> V): V =
    val peerRepr = peer[P]
    val effect = effectImpl[P]()
    val handler = new Locix.Handler[PlacedValue.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effect))
    given PeerScope[P] = PeerScope[P]()
    Locix.handle(program)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]() = new Effect:
    opaque type Id[V] = V
    override def on[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P =
      given PeerScope[P] = PeerScope[P]()
      given PlacedLabel = new PlacedLabel
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
    def on[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P

    def take[P <: Peer](using PeerScope[P])[V](value: V on P): V
end PlacedValue
