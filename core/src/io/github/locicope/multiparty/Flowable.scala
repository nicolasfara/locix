package io.github.locicope.multiparty

import io.github.locicope.Peers.Peer
import io.github.locicope.multiparty.network.Network
import ox.flow.Flow

trait Flowable[Flowing[_, _ <: Peer]]:
  def lift[V, P <: Peer](value: Flow[V], isLocal: Boolean)(using Network): Flowing[V, P]
  def unlift[V, P <: Peer](value: Flowing[Flow[V], P])(using Network): Flow[V]
