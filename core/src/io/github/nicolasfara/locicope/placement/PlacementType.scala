package io.github.nicolasfara.locicope.placement

import Peers.Peer
import io.github.nicolasfara.locicope.network.{Network, NetworkResource}
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}
import ox.flow.Flow

object PlacementType:
  infix opaque type on[+V, -P <: Peer] = PlacedType[V, P]
  infix opaque type flowOn[+V, -P <: Peer] = PlacedFlowType[V, P]

  private enum PlacedType[+V, -P <: Peer]:
    case Local(value: V, resourceReference: ResourceReference)
    case Remote(resourceReference: ResourceReference)

  private enum PlacedFlowType[+V, -P <: Peer]:
    case Local(value: Flow[V], resourceReference: ResourceReference)
    case Remote(resourceReference: ResourceReference)

  given PlaceableValue[on] with
    override def lift[V: Encoder, P <: Peer](value: Option[V], resourceReference: ResourceReference)(using Network): V on P =
      value match
        case Some(value) =>
          summon[Network].registerValue(value, resourceReference)
          PlacedType.Local(value, resourceReference)
        case None => PlacedType.Remote(resourceReference)

    override def unlift[V: Decoder, P <: Peer](value: V on P)(using Network): V = value match
      case PlacedType.Local(value, _) => value
      case PlacedType.Remote(resourceReference) => summon[Network].getValue(resourceReference) match
        case Right(v) => v
        case Left(error) => throw new RuntimeException(s"Error retrieving value: $error")

  given PlaceableFlow[flowOn] with
    override type Container[V] = Flow[V]
    override def lift[V, P <: Peer](value: Flow[V], isLocal: Boolean)(using Network): V flowOn P = ???
    override def unlift[V, P <: Peer](value: Flow[V] flowOn P)(using Network): Flow[V] = ???
end PlacementType
