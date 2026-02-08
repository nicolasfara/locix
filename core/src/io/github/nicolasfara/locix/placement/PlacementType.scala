package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.peers.Peers.*
import PlacementType.*
import scala.caps.Mutable
import io.github.nicolasfara.locix.network.Identifier
import io.github.nicolasfara.locix.network.Network

trait PlacementType:

  infix type on[+V, -P <: Peer]

  trait PeerScope[+P <: Peer]:
    def id: Identifier

  sealed trait OnGuard extends Mutable

  protected[locix] def newOnGuard: OnGuard^ = new OnGuard {}

  def on[P <: Peer: PeerTag, V](using OnGuard^, Network)(body: PeerScope[P] ?=> V): V on P
  def take[P <: Peer, V](using ps: PeerScope[P])(placement: V on P): V

  protected[locix] def local[V, P <: Peer](value: V, key: Identifier): V on P
  protected[locix] def remote[V, P <: Peer](key: Identifier): V on P
  protected[locix] def getKey[V, P <: Peer](value: V on P): Identifier
  protected[locix] def getLocalValue[V, P <: Peer](value: V on P): Option[V]

object PlacementType:
  infix type on[+V, -P <: Peer] = PlacementType#on[V, P]

  def on[P <: Peer: PeerTag](using pt: PlacementType^, guard: pt.OnGuard^, n: Network)[V](body: pt.PeerScope[P] ?=> V): pt.on[V, P] =
    pt.on(body)

  def take[P <: Peer, V](using pt: PlacementType^, scope: pt.PeerScope[P])(placement: pt.on[V, P]): V =
    pt.take(placement)
