package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.PlacementType.PeerScope
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

object PlacedValue:
  type PlacedValue = Locicope[PlacedValue.Effect]

  extension [Remote <: Peer](using pl: PlacedValue)(placedValue: pl.effect.on[pl.effect.Value, Remote])
    def unwrap(using PeerScope[Remote], Encoder[pl.effect.Value]): pl.effect.Value = pl.effect.unwrap(placedValue)

    inline def locally[Local <: TiedWith[Remote]](using
        ps: PeerScope[Local],
        net: Network,
        cdc: Codec[pl.effect.Value],
    ): Map[net.effect.Id, pl.effect.Value] = pl.effect.locally[Remote, Local](peer[Remote])(placedValue)

    inline def asLocal[Local <: TiedToSingle[Remote]](using
        PeerScope[Local],
        Network,
        Codec[pl.effect.Value],
    ): pl.effect.Value =
      pl.effect.asLocal[Remote, Local](peer[Remote])(placedValue)

    inline def asLocalAll[Local <: TiedToMultiple[Remote]](using
        ps: PeerScope[Local],
        net: Network,
        cdc: Codec[pl.effect.Value],
    ): Map[net.effect.Id, pl.effect.Value] =
      pl.effect.asLocalAll[Remote, Local](peer[Remote])(placedValue)
  end extension

  inline def run[P <: Peer](using net: Network)[V](program: PlacedValue ?=> V): V =
    given pv: Locicope.Handler[PlacedValue.Effect, V, V] = new HandlerImpl
    Locicope.handle(program)(using pv)

  private class HandlerImpl[V] extends Locicope.Handler[PlacedValue.Effect, V, V]:
    override def handle(program: Locicope[Effect] ?=> V): V = program(using new Locicope(EffectImpl[V]()))

  private class EffectImpl[V] extends Effect:
    type Value = V

    override def unwrap[Local <: Peer](using PeerScope[Local])(placedValue: Value on Local): Value =
      // https://dotty.epfl.ch/docs/reference/other-new-features/runtimeChecked.html#example
      val Placed.Local(value, _) = placedValue.runtimeChecked
      value

    override def locally[Remote <: Peer, Local <: TiedWith[Remote]](using
        scope: PeerScope[Local],
        net: Network,
        cdc: Decoder[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value] =
      val Placed.Remote(reference) = placedValue.runtimeChecked
      val peers = reachablePeers[Remote](remotePeerRepr)
      peers
        .map: address =>
          val id = address.id
          val receivedValue = receive(address, reference).fold(error => throw error, identity)
          id -> receivedValue
        .toMap

    override def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        PeerScope[Local],
        Network,
        Codec[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Value =
      val peers = reachablePeers[Remote](remotePeerRepr)
      if peers.size != 1 then throw IllegalStateException(s"Expected exactly one remote peer, found ${peers.size}.")
      val address = peers.head
      val Placed.Remote(reference) = placedValue.runtimeChecked
      receive(address, reference).fold(error => throw error, identity)

    override def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        ps: PeerScope[Local],
        net: Network,
        cdc: Codec[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value] =
      val Placed.Remote(reference) = placedValue.runtimeChecked
      val peers = reachablePeers[Remote](remotePeerRepr)
      peers
        .map: address =>
          val id = address.id
          val receivedValue = receive(address, reference).fold(error => throw error, identity)
          id -> receivedValue
        .toMap

  end EffectImpl

  trait Effect extends Placement:
    /**
     * Extract the placed value. This method can only be called by the peer that owns the value. Any other try to call this method on a placed value
     * on a remote peer will result in a compile-time error.
     *
     * @param placedValue
     *   the placed value to extract the value from.
     * @return
     *   the value wrapped in the placed value.
     */
    def unwrap[Local <: Peer](using PeerScope[Local])(placedValue: Value on Local): Value

    /**
     * Given a placed value on a remote peer tied with the local peer, fetch all the instances of the value from the remote peer. Any call to this
     * method on a placed value that is not on a remote peer or not tied with the local peer will result in a compile-time error.
     *
     * @param placedValue
     *   the placed value to fetch the instances from.
     * @return
     *   a map of the ids of the remote peers and the corresponding values.
     */
    def locally[Remote <: Peer, Local <: TiedWith[Remote]](using
        ps: PeerScope[Local],
        net: Network,
        cdc: Decoder[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value]

    /**
     * Given a placed value on a remote peer tied with the local peer, fetch the instance of the value from the remote peer. This method can only by
     * called if the local peer is tied to a single remote peer. Any call to this method on a placed value that is not on a remote peer or not tied
     * with the local peer will result in a compile-time error.
     *
     * @param placedValue
     *   the placed value to fetch the instance from.
     * @return
     *   the instance of the value.
     */
    def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        PeerScope[Local],
        Network,
        Codec[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Value

    /**
     * Given a placed value on multiple remote peers tied with the local peer, fetch all the instances of the value from the remote peers. This method
     * can only by called if the local peer is tied to multiple remote peers. Any call to this method on a placed value that is not on a remote peer
     * or not tied with the local peer will result in a compile-time error.
     *
     * @param placedValue
     *   the placed value to fetch the instances from.
     * @return
     *   a map of the ids of the remote peers and the corresponding values.
     */
    def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        ps: PeerScope[Local],
        net: Network,
        cdc: Codec[Value],
    )(remotePeerRepr: PeerRepr)(placedValue: Value on Remote): Map[net.effect.Id, Value]
  end Effect
end PlacedValue
