package io.github.nicolasfara.locicope.placement

import Peers.{ Peer, PeerRepr }
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
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

    override def unliftAll[V: Decoder, P <: Peer](value: on[V, P])(using net: Network): Map[net.ID, V] = value match
      case PlacedType.Local(value, _) => Map(net.localId -> value)
      case PlacedType.Remote(resourceReference) => net.getAllValues[V](resourceReference)

    override def unliftFlowAll[V: Decoder, P <: Peer](value: on[Flow[V], P])(using net: Network): Flow[(net.ID, V)] = value match
      case PlacedType.Local(value, _) => value.map(elem => (net.localId, elem))
      case PlacedType.Remote(resourceReference) =>
        net.getAllFlows[V](resourceReference) match
          case Right(flow) => flow
          case Left(error) => throw new RuntimeException(s"Error retrieving flow: $error")

    override def comm[V: Codec, Sender <: Peer, Receiver <: Peer](value: on[V, Sender], localPeerRepr: PeerRepr)(using Network): on[V, Receiver] =
      value match
        case PlacedType.Local(value, ref) =>
          summon[Network].registerValue(value, ref)
          PlacedType.Remote(ResourceReference(ref.resourceId, localPeerRepr, ref.valueType))
        case PlacedType.Remote(ref) =>
          summon[Network].getValue(ref) match
            case Right(value) => PlacedType.Local(value, ResourceReference(ref.resourceId, localPeerRepr, ref.valueType))
            case _ => throw new RuntimeException(s"Error retrieving value for comm: $ref")

  end given
end PlacementType
