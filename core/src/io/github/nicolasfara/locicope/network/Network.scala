package io.github.nicolasfara.locicope.network

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

enum NetworkError:
  case ValueNotRegistered

trait Network:
  type ID

  def localId: ID

  def registerValue[V: Encoder](value: V, produced: Reference): Unit
  def registerFlow[V: Encoder](flow: Flow[V], produced: Reference): Unit
  def registerFunction[In <: Product: Encoder, Out: Encoder, On[_, _ <: Peer]](function: Multitier#PlacedFunction[In, Out, On, ?]): Unit

  def getValue[V](produced: Reference)(using Decoder[V]): Either[NetworkError, V]
  def getAllValues[V: Decoder](produced: Reference): Map[ID, V]
  def getFlow[V: Decoder](produced: Reference): Either[NetworkError, Flow[V]]
  def getAllFlows[V: Decoder](produced: Reference): Either[NetworkError, Flow[(ID, V)]]

  def callFunction[In <: Product: Codec, Out: Codec, Pl <: Peer, P[_, _ <: Peer]: Placeable](inputs: In, resourceReference: Reference): Out
