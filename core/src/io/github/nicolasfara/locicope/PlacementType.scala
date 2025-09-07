package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{ Peer, Quantifier, TiedToMultiple, TiedToSingle }
import io.github.nicolasfara.locicope.serialization.Encoder
import ox.flow.Flow

object PlacementType:
  opaque infix type on[V, P <: Peer] = Placement[V, P]

  private enum Placement[V, P <: Peer]:
    case Local(value: V, ref: ResourceReference)
    case LocalAll(value: Map[?, V], ref: ResourceReference)
    case Remote(ref: ResourceReference)

  trait PeerScope[P <: Peer]

  def lift[V: Encoder, P <: Peer](value: Option[V], ref: ResourceReference): V on P = ???
  def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], ref: ResourceReference): Flow[V] on P = ???

  extension [V, Remote <: Peer](p: on[V, Remote])
    def asFlow[Local <: { type Tie <: Quantifier[Remote] }](using PeerScope[Local], Net): Flow[V] = ???
    def unwrap(using PeerScope[Remote], Net): V = ???
    def asLocal[Local <: TiedToSingle[Remote]](using PeerScope[Local], Net): V = ???
    def asLocalAll[Local <: TiedToMultiple[Remote]](using PeerScope[Local], Net): Map[Int, V] = ???
