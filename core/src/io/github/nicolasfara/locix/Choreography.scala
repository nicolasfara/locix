package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.placement.PlacementType
import scala.compiletime.Erased
import scala.caps.SharedCapability
import scala.caps.Control
import io.github.nicolasfara.locix.network.Network

trait Choreography extends Multiparty:
  def comm[S <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using Network, PlacementType)[V](placement: V on S): V on R
  def multicast[S <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using Network, PlacementType)[V](placement: V on S): V on R
  def broadcast[S <: Peer: PeerTag, V](using Network, PlacementType)(placement: V on S): V

object Choreography:
  def comm[S <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )[V, SC <: Multiparty](placement: V on S)(using Scope[SC], SC =:= Choreography): V on R = c.comm[S, R](placement)

  def multicast[S <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )[V, SC <: Multiparty](placement: V on S)(using Scope[SC], SC =:= Choreography): V on R = c.multicast[S, R](placement)

  def broadcast[S <: Peer: PeerTag, V](using
    c: Choreography,
    n: Network,
    p: PlacementType,
  )(placement: V on S)[SC <: Multiparty](using Scope[SC], SC =:= Choreography): V = c.broadcast[S, V](placement)

  def apply[A](using Choreography^)(choreography: Scope[Choreography] ?=> A): A =
    val scope = new Scope[Choreography] {}
    choreography(using scope)