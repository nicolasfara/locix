package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.peers.Peers.{Peer, PeerTag, TiedManyWith, TiedSingleWith}
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.Choreography
import io.github.nicolasfara.locix.network.{Network, NetworkError, Identifier}
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.utils.Utils.{selectWithDefault, select}
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.raise.Raise.*
import io.github.nicolasfara.locix.errors.LocixError


private final class ChoreographyEffectImpl[P <: Peer: PeerTag](using r: Raise[NetworkError]) extends Choreography:
  this: ChoreographyEffectImpl[P]^{r} =>

  private val namespace = Some("choreography")
  private val local = summon[PeerTag[P]]

  def comm[S <: TiedSingleWith[R]: PeerTag, R <: Peer: PeerTag](using p: PlacementType, n: Network)[V](placement: p.on[V, S]): p.on[V, R] =
    val sender = summon[PeerTag[S]]
    val receiver = summon[PeerTag[R]]
    val key = p.getKey(placement)
    selectWithDefault(local, sender, receiver)(
      onLocal = {
        val reachablePeers = n.reachablePeersOf[R]
        ensure(reachablePeers.size == 1) {
          NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${receiver}, but found ${reachablePeers.size}")
        }
        val remotePeer = reachablePeers.head
        val value = p.getLocalValue(placement).value(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString)))
        n.push[S, R, V](remotePeer, key, value)
        p.remote(key)
      },
      onRemote = {
        val reachablePeers = n.reachablePeersOf[S]
        ensure(reachablePeers.size == 1) {
          NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${sender}, but found ${reachablePeers.size}")
        }
        val remotePeer = reachablePeers.head
        val value = n.pull[S, R, V](remotePeer, key)
        p.local(value, key)
      },
      default = p.remote(key)
    )
  def multicast[S <: TiedManyWith[R]: PeerTag, R <: Peer: PeerTag](using p: PlacementType, n: Network)[V](placement: p.on[V, S]): p.on[V, R] =
    val sender = summon[PeerTag[S]]
    val receiver = summon[PeerTag[R]]
    val key = p.getKey(placement)
    selectWithDefault(local, sender, receiver)(
      onLocal = {
        val reachablePeers = n.reachablePeersOf[R]
        ensure(reachablePeers.nonEmpty) {
          NetworkError.SinglePeerExpected(s"Expected at least one reachable peer of type ${receiver}, but found none")
        }
        val value = p.getLocalValue(placement).value(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString)))
        reachablePeers.foreach(n.push[S, R, V](_, key, value))
        p.remote(key)
      },
      onRemote = {
        val senderPeer = n.reachablePeersOf[S]
        ensure(senderPeer.size == 1) {
          NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${sender}, but found ${senderPeer.size}")
        }
        val value = n.pull[S, R, V](senderPeer.head, key)
        p.local(value, key)
      },
      default = p.remote(key)
    )
  def broadcast[S <: Peer: PeerTag, V](using p: PlacementType, n: Network)(placement: p.on[V, S]): V =
    val sender = summon[PeerTag[S]]
    val key = p.getKey(placement)
    val value = select(local, sender)(
      onLocal = p.getLocalValue(placement).value(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString))),
      onRemote = n.retrieve(key)
    )
    n.broadcast(key, value)
    value

object ChoreographyHandler:
  def run[P <: Peer: PeerTag](using r: Raise[NetworkError])[V](program: Choreography ?=> V): V =
    given (ChoreographyEffectImpl[P]^{r}) = ChoreographyEffectImpl[P]
    program
