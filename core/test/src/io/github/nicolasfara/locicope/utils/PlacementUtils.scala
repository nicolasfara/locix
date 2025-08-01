package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.{Network, NetworkResource}
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{Peer, peer}
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.serialization.{Decoder, Encoder}
import ox.flow.Flow

object PlacementUtils:
  transparent inline def remotePlacement[T: Encoder, P <: Peer](value: T)(net: Network)(using pv: Placeable[on]): T on P =
    pv.lift(Some(value), ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Value))(using summon, net)
  transparent inline def remoteFlowPlacement[T: Encoder, P <: Peer](value: Flow[T])(using pv: Placeable[on], net: Network): Flow[T] on P =
    pv.liftFlow(Some(value), ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Flow))
  transparent inline def localPlacement[T: Encoder, P <: Peer](value: T)(using pv: Placeable[on], net: Network): T on P =
    pv.lift(Some(value), ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Value))
  transparent inline def localFlowPlacement[T: Encoder, P <: Peer](value: Flow[T])(using pv: Placeable[on], net: Network): Flow[T] on P =
    pv.liftFlow(Some(value), ResourceReference(hashBody(value), peer[P], NetworkResource.ValueType.Flow))
