package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.placement.PlacementType
import scala.compiletime.Erased
import scala.caps.SharedCapability
import scala.caps.Control
import io.github.nicolasfara.locix.network.Network

trait Choreography extends Multiparty:
  trait ChoreographyScope extends Scope[Choreography]

  def comm[S <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using p: PlacementType, n: Network)[V](placement: p.on[V, S]): p.on[V, R]
  def multicast[S <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using p: PlacementType, n: Network)[V](placement: p.on[V, S]): p.on[V, R]
  def broadcast[S <: Peer: PeerTag, V](using p: PlacementType, n: Network)(placement: p.on[V, S]): V

object Choreography:
  def comm[S <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using
    p: PlacementType,
    n: Network,
    c: Choreography,
    s: c.ChoreographyScope
  )[V](placement: p.on[V, S]): p.on[V, R] = c.comm[S, R](placement)

  def multicast[S <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using
    p: PlacementType,
    n: Network,
    c: Choreography,
    s: c.ChoreographyScope
  )[V](placement: p.on[V, S]): p.on[V, R] = c.multicast[S, R](placement)

  def broadcast[S <: Peer: PeerTag, V](using
    p: PlacementType,
    n: Network,
    c: Choreography,
    s: c.ChoreographyScope
  )(placement: p.on[V, S]): V = c.broadcast[S, V](placement)

  def apply[A](using c: Choreography, p: PlacementType, n: Network)(choreography: (c.ChoreographyScope, p.OnGuard^) ?->{c, n} A): A =
    given c.ChoreographyScope = new c.ChoreographyScope {}
    given (p.OnGuard^) = p.newOnGuard
    choreography
