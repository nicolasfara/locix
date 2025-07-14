package io.github.locicope.multiparty

import io.github.locicope.Peers.Peer
import io.github.locicope.multiparty.network.Network

trait Placeable[Placed[_, _ <: Peer]]:
  def lift[V, P <: Peer](value: V, isLocal: Boolean)(using Network): Placed[V, P]
  def unlift[V, P <: Peer](value: Placed[V, P])(using Network): V