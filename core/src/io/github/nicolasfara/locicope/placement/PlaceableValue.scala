package io.github.nicolasfara.locicope.placement

import Peers.{Peer, PeerRepr}
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}

trait PlaceableValue[Placed[_, _ <: Peer]]:
  def lift[V: Encoder, P <: Peer](value: Option[V], resourceReference: ResourceReference)(using Network): Placed[V, P]
  def unlift[V: Decoder, P <: Peer](value: Placed[V, P])(using Network): V
