package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Net.{Net, getFlow, getValue}
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{Peer, Quantifier, TiedToMultiple, TiedToSingle}
import io.github.nicolasfara.locicope.serialization.{Codec, Encoder}
import ox.flow.Flow

object PlacementType:
  opaque infix type on[V, P <: Peer] = Placement[V, P]

  private enum Placement[V, P <: Peer]:
    case Local(value: V, ref: ResourceReference)
    case LocalAll(value: Map[?, V], ref: ResourceReference)
    case Remote(ref: ResourceReference)

  trait PeerScope[P <: Peer]

  def lift[V: Encoder, P <: Peer](value: Option[V], ref: ResourceReference)(using net: Net): V on P =
    value
      .map: value =>
        Net.setValue(value, ref)
        Placement.Local(value, ref)
      .getOrElse(Placement.Remote(ref))

  def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], ref: ResourceReference)(using net: Net): Flow[V] on P =
    value
      .map: flow =>
        Net.setFlow(flow, ref)
        Placement.Local(flow, ref)
      .getOrElse(Placement.Remote(ref))
      
  extension [V: Codec, Remote <: Peer](p: Flow[V] on Remote)
    def unwrap(using PeerScope[Remote], Net): Flow[V] = p match
      case Placement.Local(value, _) => value
      case Placement.LocalAll(value, ref) => throw IllegalStateException("Something went wrong, please report this issue.")
      case Placement.Remote(ref) => getFlow(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)

  extension [V: Codec, Remote <: Peer](p: on[V, Remote])
    def asFlow[Local <: { type Tie <: Quantifier[Remote] }](using PeerScope[Local], Net): Flow[V] = ???
    def unwrap(using PeerScope[Remote], Net): V = p match
      case Placement.Local(value, _) => value
      case Placement.LocalAll(value, ref) => throw IllegalStateException("Something went wrong, please report this issue.")
      case Placement.Remote(ref) => getValue(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
    def asLocal[Local <: TiedToSingle[Remote]](using PeerScope[Local], Net): V = ???
    def asLocalAll[Local <: TiedToMultiple[Remote]](using PeerScope[Local], Net): Map[Int, V] = ???
end PlacementType
