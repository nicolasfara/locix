package io.github.locicope.placement

import Peers.Peer
import io.github.locicope.network.Network

trait PlaceableValue[Placed[_, _ <: Peer]]:
  def lift[V, P <: Peer](value: V, isLocal: Boolean)(using Network): Placed[V, P]
  def unlift[V, P <: Peer](value: Placed[V, P])(using Network): V