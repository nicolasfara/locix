package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import scala.compiletime.Erased
import io.github.nicolasfara.locix.placement.{PlacementType}
import io.github.nicolasfara.locix.network.Network

trait Multitier extends Multiparty:
  trait MultitierScope extends Scope[Multitier]

  type PerPeer[Id, V]

  extension [Id, V](perPeer: PerPeer[Id, V])
    def get(peerId: Id): Option[V]
    def getOrElse(peerId: Id, default: -> V): V
    def mapValues[U](f: V -> U): PerPeer[Id, U]
    def fold[U](initial: U)(f: (U, Id, V) -> U): U

  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V](using
      n: Network,
      p: PlacementType^,
      s: p.PeerScope[L]
  )(placement: p.on[V, R]): V

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V](using
      n: Network,
      p: PlacementType^,
      s: p.PeerScope[L]
  )(placement: p.on[V, R]): PerPeer[n.PeerAddress, V]

object Multitier:
  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V](using
    m: Multitier,
    p: PlacementType^,
    ps: p.PeerScope[L],
    n: Network,
    s: m.MultitierScope
  )(placement: p.on[V, R]): V = m.asLocal(placement)

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V](using
    m: Multitier,
    p: PlacementType^,
    ps: p.PeerScope[L],
    n: Network,
    s: m.MultitierScope
  )(placement: p.on[V, R]): m.PerPeer[n.PeerAddress, V] = m.asLocalAll(placement)

  def apply[A](using m: Multitier^, p: PlacementType^, n: Network^)(multitier: (m.MultitierScope, p.OnGuard^) ?->{m, p, n} A): A =
    given m.MultitierScope = new m.MultitierScope {}
    given (p.OnGuard^) = p.newOnGuard
    multitier
