package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}

object Net:
  type Net = Locicope[Net.Effect]

  enum NetError extends Throwable:
    case ValueNotRegistered

  def getValue[V: Decoder](ref: ResourceReference)(using net: Net): Either[NetError, V] =
    net.effect.getValue[V](ref)

  def getValues[V: Decoder](ref: ResourceReference)(using net: Net): Either[NetError, Map[Int, V]] =
    net.effect.getValues[V](ref)
    
  def setValue[V: Encoder](value: V, ref: ResourceReference)(using net: Net): Unit =
    net.effect.setValue[V](value, ref)

  trait Effect:
    def getValue[V: Decoder](ref: ResourceReference): Either[NetError, V]
    def getValues[V: Decoder](ref: ResourceReference): Either[NetError, Map[Int, V]]
    def setValue[V: Encoder](value: V, ref: ResourceReference): Unit