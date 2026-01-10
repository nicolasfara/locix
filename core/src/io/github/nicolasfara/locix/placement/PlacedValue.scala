package io.github.nicolasfara.locix.placement

import scala.annotation.nowarn
import scala.util.NotGiven

import io.github.nicolasfara.locix.Locix
import io.github.nicolasfara.locix.macros.ASTHashing.hashBody
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.network.NetworkResource.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import cats.Id

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
  def on[P <: Peer: PeerRepr](using
      pv: PlacedValue,
      net: Network,
      ng: NotGiven[PlacedLabel],
  )[Value](expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P =
    pv.effect.on[P, Value](expression)

  def take[P <: Peer](using pv: PlacedValue, net: Network, scope: PeerScope[P])[V](value: V on P): V =
    pv.effect.take(value)

  extension [Remote <: Peer, Value](using pl: PlacedValue)(placedValue: Value on Remote)
    def take(using PeerScope[Remote]): Value =
      pl.effect.take(placedValue)

  def run[P <: Peer: PeerRepr](using net: Network)[V](program: (PlacedValue, PeerScope[P]) ?=> V): V =
    val handler = new Locix.Handler[PlacedValue.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effectImplementation[P]))
    given PeerScope[P] = PeerScope[P]()
    Locix.handle(program)(using handler)

  private def effectImplementation[LocalPeer <: Peer: PeerRepr] = new Effect:
    override def take[P <: Peer](using PeerScope[P])[V](value: V on P): V =
      val PlacementType.Placed.Local[V @unchecked, P @unchecked](localValue, _) = value.runtimeChecked
      localValue
    override def on[P <: Peer: PeerRepr, Value](using
        Network,
        NotGiven[PeerScope[P]],
    )(expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P =
      given PeerScope[P] = PeerScope[P]()
      given PlacedLabel = new PlacedLabel
      val peerRepr = summon[PeerRepr[P]]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Value)
      val placedValue: Option[Id[Value]] = if summon[PeerRepr[LocalPeer]] <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(placedValue, resourceReference)
  end effectImplementation

  trait Effect extends PlacementType.Placement:
    def on[P <: Peer: PeerRepr, Value](using
        Network,
        NotGiven[PeerScope[P]],
    )(expression: (PeerScope[P], PlacedLabel) ?=> Value): Value on P

    def take[P <: Peer](using PeerScope[P])[V](value: V on P): V
end PlacedValue
