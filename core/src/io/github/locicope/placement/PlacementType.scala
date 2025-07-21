package io.github.locicope.placement

import Peers.Peer
import io.github.locicope.network.Network
import ox.flow.Flow

object PlacementType:
  infix opaque type on[+V, -P <: Peer] = PlacedType[V, P]
  infix opaque type flowOn[+V, -P <: Peer] = PlacedFlowType[V, P]

  private enum PlacedType[+V, -P <: Peer]:
    case Local(value: V, remoteReference: String)
    case Remote(remoteReference: String)

  private enum PlacedFlowType[+V, -P <: Peer]:
    case Local(value: Flow[V], remoteReference: String)
    case Remote(remoteReference: String)

  given PlaceableValue[on] with
    override def lift[V, P <: Peer](value: V, isLocal: Boolean)(using Network): V on P = ???
    override def unlift[V, P <: Peer](value: V on P)(using Network): V = ???

  given oxFlow: PlaceableFlow[flowOn] with
    override type Container[V] = Flow[V]
    override def lift[V, P <: Peer](value: Flow[V], isLocal: Boolean)(using Network): V flowOn P = ???
    override def unlift[V, P <: Peer](value: Flow[V] flowOn P)(using Network): Flow[V] = ???
