package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import scala.compiletime.Erased
import io.github.nicolasfara.locix.placement.{PlacementType}
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Signal
import io.github.nicolasfara.locix.placement.PlacementType.on

trait Multitier extends Multiparty:
  infix type |~>[Id, V]

  extension [Id, V](perPeer: Id |~> V)
    def get(peerId: Id): Option[V]
    def getOrElse(peerId: Id, default: -> V): V
    def mapValues[U](f: V -> U): Id |~> U
    def fold[U](initial: U)(f: (U, Id, V) -> U): U

  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V](using Network)(placement: V on R): V

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V](using n: Network)(placement: V on R): n.PeerAddress |~> V

object Multitier:
  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V, S <: Multiparty](using
    m: Multitier,
    n: Network,
    s: Scope[S],
    ev: S =:= Multitier
  )(placement: V on R): V = m.asLocal[L, R, V](placement)

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V, S <: Multiparty](using
    m: Multitier,
    n: Network,
    s: Scope[S],
    ev: S =:= Multitier
  )(placement: V on R): m.|~>[n.PeerAddress, V] = m.asLocalAll[L, R, V](placement)

  def apply[A](using Multitier)(multitier: Scope[Multitier] ?=> A): A =
    given Scope[Multitier] = new Scope[Multitier] {}
    multitier
