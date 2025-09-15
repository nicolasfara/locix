package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import ox.flow.Flow
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.placement.Peers.Peer

object Net:
  type Net = Locicope[Net.Effect]

  enum NetError extends Throwable:
    case ValueNotRegistered

  def id(using net: Net): Int = net.effect.id

  def getValue[V: Decoder](ref: Reference)(using net: Net): Either[NetError, V] =
    net.effect.getValue[V](ref)

  def getValues[V: Decoder](ref: Reference)(using net: Net): Either[NetError, Map[Int, V]] =
    net.effect.getValues[V](ref)

  def setValue[V: Encoder](value: V, ref: Reference)(using net: Net): Unit =
    net.effect.setValue[V](value, ref)

  def setFlow[V: Encoder](value: Flow[V], ref: Reference)(using net: Net): Unit =
    net.effect.setFlow[V](value, ref)

  def getFlow[V: Decoder](ref: Reference)(using net: Net): Either[NetError, Flow[V]] =
    net.effect.getFlow[V](ref)

  def registerFunction[In <: Product: Codec, Out: Codec, P <: Peer](function: In => Out)(ref: Reference)(using net: Net): Unit =
    net.effect.registerFunction[In, Out, P](function)(ref)

  def invokeFunction[In <: Product: Codec, Out: Codec](inputs: In, ref: Reference)(using net: Net): Out =
    net.effect.invokeFunction[In, Out](inputs, ref)

  def run[V](program: Net ?=> V): V =
    val handler = new Locicope.Handler[Net.Effect, V, V]:
      override def handle(program: Locicope[Effect] ?=> V): V = ???
    Locicope.handle(program)(using handler)

  trait Effect:
    def id: Int
    def getValue[V: Decoder](ref: Reference): Either[NetError, V]
    def getValues[V: Decoder](ref: Reference): Either[NetError, Map[Int, V]]
    def setValue[V: Encoder](value: V, ref: Reference): Unit
    def setFlow[V: Encoder](value: Flow[V], ref: Reference): Unit
    def getFlow[V: Decoder](ref: Reference): Either[NetError, Flow[V]]
    def invokeFunction[In <: Product: Codec, Out: Codec](inputs: In, ref: Reference): Out
    def registerFunction[In <: Product: Codec, Out: Codec, P <: Peer](function: In => Out)(ref: Reference): Unit
end Net
