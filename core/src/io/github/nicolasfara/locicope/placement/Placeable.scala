package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.{ Peer, PeerRepr }
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

trait Placeable[Placed[_, _ <: Peer]]:
  def lift[V: Encoder, P <: Peer](value: Option[V], resourceReference: Reference)(using Network): Placed[V, P]
  def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], resourceReference: Reference)(using Network): Placed[Flow[V], P]
  def unlift[V: Decoder, P <: Peer](value: Placed[V, P])(using Network): V
  def unliftAll[V: Decoder, P <: Peer](value: Placed[V, P])(using net: Network): Map[net.ID, V]
  def unliftFlow[V: Decoder, P <: Peer](value: Placed[Flow[V], P])(using Network): Flow[V]
  def unliftFlowAll[V: Decoder, P <: Peer](value: Placed[Flow[V], P])(using net: Network): Flow[(net.ID, V)]
  def comm[V: Codec, Sender <: Peer, Receiver <: Peer](value: Placed[V, Sender], localPeerRepr: PeerRepr)(using Network): Placed[V, Receiver]
