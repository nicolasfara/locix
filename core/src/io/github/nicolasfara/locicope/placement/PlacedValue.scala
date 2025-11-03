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

object PlacedValue:
  type PlacedValue = Locicope[PlacedValue.Effect]

  class PlacedValuePeerScope[P <: Peer] extends PeerScope[P]

  inline def on[P <: Peer](using
      pv: PlacedValue,
      net: Network,
      ng: NotGiven[PeerScope[P]],
  )[Value: Codec](expression: PeerScope[P] ?=> Value): Value on P =
    pv.effect.on[P, Value](peer[P])(expression)

  def take[P <: Peer](using pv: PlacedValue, net: Network, scope: PeerScope[P])[V](value: V on P): V =
    pv.effect.take(value)

  // extension [Remote <: Peer, Value: Codec](using pl: PlacedValue)(placedValue: Value on Remote)
  //   def unwrap(using PeerScope[Remote]): Value =
  //     pl.effect.unwrap(placedValue)

  //   inline def asLocal[Local <: TiedToSingle[Remote]](using PeerScope[Local], Network): Value =
  //     pl.effect.asLocal[Remote, Local, Value](peer[Remote])(placedValue)

  //   inline def asLocalAll[Local <: TiedToMultiple[Remote]](using ps: PeerScope[Local], net: Network): Map[net.effect.Id, Value] =
  //     pl.effect.asLocalAll[Remote, Local, Value](peer[Remote])(placedValue)

  inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedValue, PeerScope[P]) ?=> V): V =
    given handler: Locicope.Handler[PlacedValue.Effect, V, V] = new HandlerImpl(peer[P])
    given PeerScope[P] = PlacedValuePeerScope[P]
    Locicope.handle(program)(using handler)

  class HandlerImpl[Value: Codec](executionPeerRepr: PeerRepr) extends Locicope.Handler[PlacedValue.Effect, Value, Value]:
    override def handle(program: Locicope[Effect] ?=> Value): Value = program(using new Locicope(EffectImpl(executionPeerRepr)))

  private class EffectImpl(executionPeerRepr: PeerRepr) extends Effect:
    type Id[V] = V
    override def on[P <: Peer, Value: Codec](using
        Network,
        NotGiven[PeerScope[P]],
    )(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Value): Value on P =
      given PeerScope[P] = new PlacedValuePeerScope[P]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Value)
      val placedValue: Option[Id[Value]] = if executionPeerRepr <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(executionPeerRepr)(placedValue, resourceReference)

    override def take[P <: Peer](using PeerScope[P])[V](value: V on P): V =
      val PlacementType.Placed.Local[V @unchecked, P @unchecked](localValue, _) = value.runtimeChecked
      localValue

    // override def unwrap[Local <: Peer, Value: Codec](using PeerScope[Local])(placedValue: Value on Local): Value =
    //   // https://dotty.epfl.ch/docs/reference/other-new-features/runtimeChecked.html#example
    //   val PlacementType.Placed.Local(value, _) = placedValue.runtimeChecked
    //   value.asInstanceOf[Value]

    // override def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote], Value: Codec](using
    //     PeerScope[Local],
    //     Network,
    // )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Value =
    //   val peers = reachablePeers[Remote](remotePeerRepr)
    //   if peers.size != 1 then throw IllegalStateException(s"Expected exactly one remote peer, found ${peers.size}.")
    //   val address = peers.head
    //   val PlacementType.Placed.Remote(reference) = placedValue.runtimeChecked
    //   receive[[V] =>> V, Value, Remote, Local](address, reference).fold(error => throw error, identity)

    // override def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], Value: Codec](using
    //     ps: PeerScope[Local],
    //     net: Network,
    // )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value] =
    //   val PlacementType.Placed.Remote(reference) = placedValue.runtimeChecked
    //   val peers = reachablePeers[Remote](remotePeerRepr)
    //   peers
    //     .map: address =>
    //       val id = address.id
    //       val receivedValue = receive[[V] =>> V, Value, Remote, Local](address, reference).fold(error => throw error, identity)
    //       id -> receivedValue
    //     .toMap

  end EffectImpl

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

    // /**
    //  * Extract the placed value. This method can only be called by the peer that owns the value. Any other try to call this method on a placed value
    //  * on a remote peer will result in a compile-time error.
    //  *
    //  * @param placedValue
    //  *   the placed value to extract the value from.
    //  * @return
    //  *   the value wrapped in the placed value.
    //  */
    // def unwrap[Local <: Peer, Value: Codec](using PeerScope[Local])(placedValue: Value on Local): Value

    // /**
    //  * Given a placed value on a remote peer tied with the local peer, fetch the instance of the value from the remote peer. This method can only by
    //  * called if the local peer is tied to a single remote peer. Any call to this method on a placed value that is not on a remote peer or not tied
    //  * with the local peer will result in a compile-time error.
    //  *
    //  * @param placedValue
    //  *   the placed value to fetch the instance from.
    //  * @return
    //  *   the instance of the value.
    //  */
    // def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote], Value: Codec](using
    //     PeerScope[Local],
    //     Network,
    // )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Value

    // /**
    //  * Given a placed value on multiple remote peers tied with the local peer, fetch all the instances of the value from the remote peers. This method
    //  * can only by called if the local peer is tied to multiple remote peers. Any call to this method on a placed value that is not on a remote peer
    //  * or not tied with the local peer will result in a compile-time error.
    //  *
    //  * @param placedValue
    //  *   the placed value to fetch the instances from.
    //  * @return
    //  *   a map of the ids of the remote peers and the corresponding values.
    //  */
    // def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], Value: Codec](using
    //     ps: PeerScope[Local],
    //     net: Network,
    // )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value]
  end Effect
end PlacedValue
