package io.github.locicope.placement

import Peers.Peer
import io.github.locicope.network.Network

trait PlaceableFlow[Placed[_, _ <: Peer]]:
  type Container[_]
  def lift[V, P <: Peer](value: Container[V], isLocal: Boolean)(using Network): Placed[V, P]
  def unlift[V, P <: Peer](value: Placed[Container[V], P])(using Network): Container[V]
