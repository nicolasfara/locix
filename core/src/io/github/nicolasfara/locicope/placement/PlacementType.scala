package io.github.nicolasfara.locicope.placement

import Peers.Peer
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import ox.flow.Flow

import scala.compiletime.erasedValue

object PlacementType:
  infix opaque type on[+V, -P <: Peer] = PlacedType[V, P]

  private enum PlacedType[+V, -P <: Peer]:
    case Local(value: V, resourceReference: ResourceReference)
    case Remote(resourceReference: ResourceReference)

  given Placeable[on] with
    override def lift[V: Encoder, P <: Peer](value: Option[V], resourceReference: ResourceReference)(using Network): on[V, P] = value match
      case Some(value) =>
        summon[Network].registerValue(value, resourceReference)
        PlacedType.Local(value, resourceReference)
      case None => PlacedType.Remote(resourceReference)

    override def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], resourceReference: ResourceReference)(using Network): on[Flow[V], P] =
      value match
        case Some(flow) =>
          summon[Network].registerFlow(flow, resourceReference)
          PlacedType.Local(flow, resourceReference)
        case None => PlacedType.Remote(resourceReference)

    override def unlift[V: Decoder, P <: Peer](value: on[V, P])(using Network): V = value match
      case PlacedType.Local(value, _) => value
      case PlacedType.Remote(resourceReference) =>
        summon[Network].getValue(resourceReference) match
          case Right(v) => v
          case Left(error) => throw new RuntimeException(s"Error retrieving value: $error")

    override def unliftFlow[V: Decoder, P <: Peer](value: on[Flow[V], P])(using Network): Flow[V] = value match
      case PlacedType.Local(value, _) => value
      case PlacedType.Remote(resourceReference) =>
        summon[Network].getFlow(resourceReference) match
          case Right(v) => v
          case Left(error) => throw new RuntimeException(s"Error retrieving flow: $error")
  end given
end PlacementType
