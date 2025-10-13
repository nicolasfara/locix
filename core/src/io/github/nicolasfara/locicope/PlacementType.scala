package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.{ Peer, TiedToMultiple, TiedToSingle, TiedWith }
import io.github.nicolasfara.locicope.serialization.{ Codec, Encoder }
import ox.flow.Flow

object PlacementType
//   opaque infix type on[V, P <: Peer] = Placement[V, P]

//   private enum Placement[V, P <: Peer]:
//     case Local(value: V, ref: Reference)
//     case LocalAll(value: Map[Int, V], ref: Reference)
//     case Remote(ref: Reference)

//   trait PeerScope[P <: Peer]

//   def lift[V: Encoder, P <: Peer](value: Option[V], ref: Reference)(using net: Net): V on P =
//     value
//       .map: value =>
//         Net.setValue(value, ref)
//         Placement.Local(value, ref)
//       .getOrElse(Placement.Remote(ref))

//   def liftFlow[V: Encoder, P <: Peer](value: Option[Flow[V]], ref: Reference)(using net: Net): Flow[V] on P =
//     value
//       .map: flow =>
//         Net.setFlow(flow, ref)
//         Placement.Local(flow, ref)
//       .getOrElse(Placement.Remote(ref))

//   protected[locicope] def getRef[V, P <: Peer](value: V on P): Reference = value match
//     case Placement.Local(_, ref) => ref
//     case Placement.LocalAll(_, ref) => ref
//     case Placement.Remote(ref) => ref

//   protected[locicope] def getLocalValue[V, P <: Peer](value: V on P)(using Net): Option[V] = value match
//     case Placement.Local(v, _) => Some(v)
//     case Placement.LocalAll(values, _) => values.get(id)
//     case Placement.Remote(_) => None

//   extension [V: Codec, Remote <: Peer](p: Flow[V] on Remote)
//     def unwrap(using PeerScope[Remote], Net): Flow[V] = p match
//       case Placement.Local(value, _) => value
//       case Placement.LocalAll(value, ref) => throw IllegalStateException("Something went wrong, please report this issue.")
//       case Placement.Remote(ref) => getFlow(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
//     def asLocal[Local <: TiedToSingle[Remote]](using PeerScope[Local], Net): Flow[V] = p match
//       case Placement.Local(value, _) => value
//       case Placement.LocalAll(value, ref) => throw IllegalStateException("Something went wrong, please report this issue.")
//       case Placement.Remote(ref) => getFlow(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)

//   extension [V: Codec, P <: Peer](p: on[V, P])
//     def unwrap(using PeerScope[P], Net): V = p match
//       case Placement.Local(value, _) => value
//       case Placement.LocalAll(values, _) => values.get(id).getOrElse(throw IllegalStateException("Value not found on local peer"))
//       case Placement.Remote(ref) => getValue(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
//     def unwrapAll(using PeerScope[P], Net): Map[Int, V] = p match
//       case Placement.Local(value, _) => Map(id -> value)
//       case Placement.LocalAll(values, _) => values
//       case Placement.Remote(ref) => getValues(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
//     def asLocal[Local <: TiedToSingle[P]](using PeerScope[Local], Net): V = p match
//       case Placement.Local(value, _) => value
//       case Placement.LocalAll(values, _) => values.get(id).getOrElse(throw IllegalStateException("Value not found on local peer"))
//       case Placement.Remote(ref) => getValue(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
//     def asLocalAll[Local <: TiedToMultiple[P]](using PeerScope[Local], Net): Map[Int, V] = p match
//       case Placement.Local(value, _) => Map(id -> value)
//       case Placement.LocalAll(values, _) => values
//       case Placement.Remote(ref) => getValues(ref).fold(ex => throw IllegalStateException("Value not found", ex), identity)
// end PlacementType
