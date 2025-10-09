package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.network.Network.register

trait Placement:
  type Value
  opaque infix type on[+V <: Value, -P <: Peer] = Placed[V, P]

  protected enum Placed[+V <: Value, -P <: Peer]:
    case Local(value: V, ref: Reference)
    case Remote(ref: Reference)

  def lift(using Network, Encoder[Value])(value: Option[Value], ref: Reference): Value on Peer =
    value
      .map: value =>
        register(ref, value)
        Placed.Local(value, ref)
      .getOrElse:
        Placed.Remote(ref)
