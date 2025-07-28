package io.github.nicolasfara.locicope.network

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

enum NetworkError:
  case ValueNotRegistered

trait Network:
  type ID

  def registerValue[V: Encoder](value: V, produced: ResourceReference): Unit
  def registerFlow[V: Encoder](flow: Flow[V], produced: ResourceReference): Unit
  def registerFunction[In <: Product: Encoder, Out: Encoder, F[_, _ <: Peer]](function: Multitier#PlacedFunction[?, In, Out, F]): Unit

  def getValue[V: Decoder](produced: ResourceReference): Either[NetworkError, V]
  def getFlow[V: Decoder](produced: ResourceReference): Either[NetworkError, Flow[V]]

  def callFunction[In <: Product: Codec, Out: Codec, Pl <: Peer, P[_, _ <: Peer]: Placeable](inputs: In, resourceReference: ResourceReference): Out
