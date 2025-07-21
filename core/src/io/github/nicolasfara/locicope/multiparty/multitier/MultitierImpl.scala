package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.{Peer, PeerRepr, TiedToMultiple, TiedToSingle}
import io.github.nicolasfara.locicope.placement.{PlaceableFlow, PlaceableValue}
import io.github.nicolasfara.locicope.serialization.{Codec, Decoder, Encoder}
import ox.flow.Flow

import scala.util.NotGiven

protected class MultitierImpl(override protected val localPeerRepr: PeerRepr) extends Multitier:

  override inline def function[In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: PlaceableValue, Local <: Peer](
      body: MultitierLabel[Local] ?=> In => Out,
  )(using NotGiven[MultitierLabel[Local]]): PlacedFunction[Local, In, Out, P] = ???

  override protected def _asLocal[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: PlaceableValue](
      effect: F[V, Remote],
  )(using Network, MultitierLabel[Local]): V = summon[PlaceableValue[F]].unlift(effect)

  override protected def _asLocalAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: PlaceableValue](
      effect: F[V, Remote],
  )(using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V] = ???

  override protected def _asLocalFlow[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: PlaceableFlow](
      flow: F[Flow[V], Remote],
  )(using Network, MultitierLabel[Local]): Flow[V] = ???

  override protected def _asLocalFlowAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: PlaceableFlow](
      flow: F[Flow[V], Remote],
  )(using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)] = ???
end MultitierImpl
