package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.peers.Peers.{Peer, PeerTag}
import io.github.nicolasfara.locix.network.{Network, NetworkError, Identifier}
import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.peers.Peers.TiedManyWith
import io.github.nicolasfara.locix.peers.Peers.TiedSingleWith
import io.github.nicolasfara.locix.raise.Raise.ensure
import io.github.nicolasfara.locix.placement.Signal
import java.util.concurrent.ConcurrentLinkedQueue
import io.github.nicolasfara.locix.placement.PlacementType.on

private final class MultitierEffectImpl[P <: Peer: PeerTag](using r: Raise[NetworkError]) extends Multitier:
  type |~>[Id, V] = Map[Id, V]

  extension [Id, V](perPeer: Id |~> V) def get(peerId: Id): Option[V] =
    perPeer.get(peerId)
  extension [Id, V](perPeer: Id |~> V) def getOrElse(peerId: Id, default: -> V): V =
    perPeer.getOrElse(peerId, default)
  extension [Id, V](perPeer: Id |~> V) def mapValues[U](f: V -> U): Id |~> U =
    perPeer.view.mapValues(f).toMap
  extension [Id, V](perPeer: Id |~> V) def fold[U](initial: U)(f: (U, Id, V) -> U): U =
    perPeer.foldLeft(initial) { case (acc, (id, value)) => f(acc, id, value) }
  extension [Id, V](perPeer: Id |~> V) def values: Iterable[V] = perPeer.values

  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V](using n: Network)(placement: V on R): V =
    val key = placement.key
    val reachablePeers = n.reachablePeersOf[R]
    ensure(reachablePeers.size == 1):
      NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${summon[PeerTag[R]]}, but found ${reachablePeers.size}")
    val remotePeer = reachablePeers.head
    n.pull(remotePeer, key)

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V](using n: Network)(placement: V on R): n.PeerAddress |~> V =
    val key = placement.key
    val reachablePeers = n.reachablePeersOf[R]
    ensure(reachablePeers.nonEmpty):
      NetworkError.SinglePeerExpected(s"Expected at least one reachable peer of type ${summon[PeerTag[R]]}, but found none")
    reachablePeers.map(peer => peer -> n.pull(peer, key)).toMap

object MultitierHandler:
  def run[P <: Peer: PeerTag](using r: Raise[NetworkError])[V](program: Multitier^ ?=> V): V =
    given (Multitier^{r}) = new MultitierEffectImpl[P]
    program

  def handler[P <: Peer: PeerTag](using Raise[NetworkError]): Multitier = new MultitierEffectImpl[P]
