package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.*
import scala.compiletime.Erased
import io.github.nicolasfara.locix.placement.{PlacementType}
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.placement.Signal
import io.github.nicolasfara.locix.placement.PlacementType.on
import scala.caps.SharedCapability
import io.github.nicolasfara.locix.placement.PeerScope

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
  sealed trait MultitierScope extends Erased

  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using
    m: Multitier,
    n: Network,
    scope: PeerScope[L]
  )[V, SC](using s: Scope[SC], ev: SC =:= MultitierScope)(placement: V on R): V = m.asLocal[L, R, V](placement)

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using
    m: Multitier,
    n: Network,
    scope: PeerScope[L]
  )[V, SC](using s: Scope[SC], ev: SC =:= MultitierScope)(placement: V on R): m.|~>[n.PeerAddress, V] = m.asLocalAll[L, R, V](placement)

  def apply[A](using Multitier)(multitier: Scope[MultitierScope] ?=> A): A =
    given Scope[MultitierScope] = new Scope[MultitierScope] {}
    multitier
