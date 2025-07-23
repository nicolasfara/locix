package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.{Network, NetworkError, NetworkResource}
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}

import scala.collection.mutable

class InMemoryNetwork extends Network:
  override type ID = Int

  private val registeredResources = mutable.Map[NetworkResource.ResourceReference, Any]()

  override def registerValue[V: Encoder](value: V, produced: NetworkResource.ResourceReference): Unit =
    registeredResources(produced) = value

  override def registerFunction[In <: Product: Encoder, Out: Encoder, F[_, _ <: Peer]](function: Multitier#PlacedFunction[?, In, Out, F]): Unit = ???

  @SuppressWarnings(Array("org.wartremover.warts.asInstanceOf"))
  override def getValue[V: Decoder](produced: NetworkResource.ResourceReference): Either[NetworkError, V] =
    registeredResources.get(produced).toRight(NetworkError.ValueNotRegistered).map(_.asInstanceOf[V])

  override def callFunction[In, Out](inputs: In, resourceReference: NetworkResource.ResourceReference): Out = ???
