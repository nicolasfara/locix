package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.{ Network, NetworkError, NetworkResource }
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

import scala.collection.mutable

class InMemoryNetwork extends Network:
  override type ID = Int

  private val registeredResources = mutable.Map[NetworkResource.ResourceReference, Any]()
  private val registeredFunctions = mutable.Map[NetworkResource.ResourceReference, Multitier#PlacedFunction[?, ?, ?, ?]]()

  override def registerValue[V: Encoder](value: V, produced: NetworkResource.ResourceReference): Unit =
    registeredResources(produced) = value

  override def registerFunction[In <: Product: Encoder, Out: Encoder, On[_, _ <: Peer]](function: Multitier#PlacedFunction[In, Out, On, ?]): Unit =
    val resourceReference = function.resourceReference
    registeredFunctions(resourceReference) = function

  @SuppressWarnings(Array("org.wartremover.warts.asInstanceOf"))
  override def getValue[V: Decoder](produced: NetworkResource.ResourceReference): Either[NetworkError, V] =
    registeredResources.get(produced).toRight(NetworkError.ValueNotRegistered).map(_.asInstanceOf[V])

  override def callFunction[In <: Product: Codec, Out: Codec, Placement <: Peer, On[_, _ <: Peer]: Placeable](
      inputs: In,
      resourceReference: NetworkResource.ResourceReference,
  ): Out =
    given net: Network = this
    registeredFunctions.get(resourceReference) match
      case Some(function) =>
        val placed = function.asInstanceOf[Multitier#PlacedFunction[In, Out, On, Placement]].apply(inputs)
        summon[Placeable[On]].unlift(placed) // Unwrap the placed value to get the actual result
      case None =>
        throw new IllegalArgumentException(s"No function registered for resource reference: $resourceReference")

  override def registerFlow[V: Encoder](flow: Flow[V], produced: NetworkResource.ResourceReference): Unit =
    registeredResources(produced) = flow

  override def getFlow[V: Decoder](produced: NetworkResource.ResourceReference): Either[NetworkError, Flow[V]] =
    registeredResources.get(produced) match
      case Some(flow: Flow[V] @unchecked) => Right(flow)
      case _ => Left(NetworkError.ValueNotRegistered)
  end getFlow
end InMemoryNetwork
