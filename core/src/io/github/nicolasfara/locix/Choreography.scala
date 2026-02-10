package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.placement.PlacementType
import scala.compiletime.Erased
import scala.caps.SharedCapability
import scala.caps.Control
import io.github.nicolasfara.locix.network.Network
import scala.caps.cap

trait Choreography extends Multiparty:
  def comm[S <: TiedSingleWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using Network)[V](placement: V on S): V on R
  def multicast[S <: TiedManyWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using Network)[V](placement: V on S): V on R
  def broadcast[S <: Peer: PeerTag, V](using Network)(placement: V on S): V

object Choreography:
  sealed trait ChoreographyScope extends Erased

  def comm[S <: TiedSingleWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )[V, SC](placement: V on S)(using Scope[SC], SC =:= ChoreographyScope): V on R = c.comm[S, R](placement)

  def multicast[S <: TiedManyWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )[V, SC](placement: V on S)(using Scope[SC], SC =:= ChoreographyScope): V on R = c.multicast[S, R](placement)

  def broadcast[S <: Peer: PeerTag, V](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )(placement: V on S)[SC](using Scope[SC], SC =:= ChoreographyScope): V = c.broadcast[S, V](placement)

  def apply[A](using c: Choreography)(choreography: Scope[ChoreographyScope] ?=> A): A =
    val scope = new Scope[ChoreographyScope] {}
    choreography(using scope)