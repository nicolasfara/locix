package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.peers.Peers.{Peer, PeerTag}
import io.github.nicolasfara.locix.network.{Network, NetworkError}
import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.peers.Peers.TiedManyWith
import io.github.nicolasfara.locix.peers.Peers.TiedSingleWith
import io.github.nicolasfara.locix.raise.Raise.ensure

private final class MultitierEffectImpl[P <: Peer: PeerTag](using r: Raise[NetworkError]) extends Multitier:
  this: MultitierEffectImpl[P]^{r} =>

  type PerPeer[Id, V] = Map[Id, V]

  extension [Id, V](perPeer: PerPeer[Id, V]) def get(peerId: Id): Option[V] =
    perPeer.get(peerId)
  extension [Id, V](perPeer: PerPeer[Id, V]) def getOrElse(peerId: Id, default: -> V): V =
    perPeer.getOrElse(peerId, default)
  extension [Id, V](perPeer: PerPeer[Id, V]) def mapValues[U](f: V -> U): PerPeer[Id, U] =
    perPeer.view.mapValues(f).toMap
  extension [Id, V](perPeer: PerPeer[Id, V]) def fold[U](initial: U)(f: (U, Id, V) -> U): U =
    perPeer.foldLeft(initial) { case (acc, (id, value)) => f(acc, id, value) }

  def asLocal[L <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag, V](using
      n: Network,
      p: PlacementType^,
      s: p.PeerScope[L]
  )(placement: p.on[V, R]): V =
    val key = p.getKey(placement)
    val reachablePeers = n.reachablePeersOf[R]
    ensure(reachablePeers.size == 1):
      NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${summon[PeerTag[R]]}, but found ${reachablePeers.size}")
    val remotePeer = reachablePeers.head
    n.pull(remotePeer, key)

  def asLocalAll[L <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag, V](using
      n: Network,
      p: PlacementType^,
      s: p.PeerScope[L]
  )(placement: p.on[V, R]): PerPeer[n.PeerAddress, V] =
    val key = p.getKey(placement)
    val reachablePeers = n.reachablePeersOf[R]
    ensure(reachablePeers.nonEmpty):
      NetworkError.SinglePeerExpected(s"Expected at least one reachable peer of type ${summon[PeerTag[R]]}, but found none")
    reachablePeers.map(peer => peer -> n.pull(peer, key)).toMap


object MultitierHandler:
  def run[P <: Peer: PeerTag](using r: Raise[NetworkError])[V](program: Multitier ?=> V): V =
    given (Multitier^{r}) = new MultitierEffectImpl[P]
    program