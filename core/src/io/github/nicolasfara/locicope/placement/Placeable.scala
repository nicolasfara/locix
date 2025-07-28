package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}
import ox.flow.Flow

trait Placeable[Placed[_, _ <: Peer]]:
  def lift[V: Encoder, P <: Peer](value: Option[V], resourceReference: ResourceReference)(using Network): Placed[V, P]
  def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], resourceReference: ResourceReference)(using Network): Placed[Flow[V], P]
  def unlift[V: Decoder, P <: Peer](value: Placed[V, P])(using Network): V
  def unliftFlow[V: Decoder, P <: Peer](value: Placed[Flow[V], P])(using Network): Flow[V]
