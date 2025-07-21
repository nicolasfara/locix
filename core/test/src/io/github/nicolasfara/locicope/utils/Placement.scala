package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.{Network, NetworkResource}
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{Peer, peer}
import io.github.nicolasfara.locicope.placement.PlaceableValue
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}

object Placement:
  transparent inline def remotePlacement[T: Encoder, P <: Peer](value: T)(using pv: PlaceableValue[on], net: Network): T on P =
    pv.lift(Some(value), ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Value))
  transparent inline def localPlacement[T: Encoder, P <: Peer](value: T)(using pv: PlaceableValue[on], net: Network): T on P =
    pv.lift(None, ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Value))
