package io.github.locicope.placement

import Peers.Peer
import io.github.locicope.network.Network
import ox.flow.Flow

trait Flowable[Flowing[_, _ <: Peer]]:
  type Container[_]
  def lift[V, P <: Peer](value: Container[V], isLocal: Boolean)(using Network): Flowing[V, P]
  def unlift[V, P <: Peer](value: Flowing[Flow[V], P])(using Network): Container[V]
