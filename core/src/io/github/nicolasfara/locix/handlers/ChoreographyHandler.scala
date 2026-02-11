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
  private val namespace = Some("choreography")
  private val local = summon[PeerTag[P]]

  def comm[S <: TiedSingleWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using n: Network)[V](placement: V on S): V on R =
    val sender = summon[PeerTag[S]]
    val receiver = summon[PeerTag[R]]
    val key = placement.key
    selectWithDefault(local, sender, receiver)(
      onLocal = {
        val reachablePeers = n.reachablePeersOf[R]
        ensure(reachablePeers.size == 1) {
          NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${receiver}, but found ${reachablePeers.size}")
        }
        val remotePeer = reachablePeers.head
        val value = placement match
          case PlacementValue.Local(v, _) => v
          case _ => raise(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString)))
        n.push[S, R, V](remotePeer, key, value)
        PlacementValue.Remote(key)
      },
      onRemote = {
        // val reachablePeers = n.reachablePeersOf[S]
        // ensure(reachablePeers.size == 1) {
        //   NetworkError.SinglePeerExpected(s"Expected exactly one reachable peer of type ${sender}, but found ${reachablePeers.size}")
        // }
        // val remotePeer = reachablePeers.head
        val value = n.retrieve[S, V](key)
        PlacementValue.Local(value, key)
      },
      default = PlacementValue.Remote(key)
    )

  def multicast[S <: TiedManyWith[R]: PeerTag, R <: TiedSingleWith[S]: PeerTag](using n: Network)[V](placement: V on S): V on R =
    val sender = summon[PeerTag[S]]
    val receiver = summon[PeerTag[R]]
    val key = placement.key
    selectWithDefault(local, sender, receiver)(
      onLocal = {
        val reachablePeers = n.reachablePeersOf[R]
        ensure(reachablePeers.nonEmpty) {
          NetworkError.SinglePeerExpected(s"Expected at least one reachable peer of type ${receiver}, but found none")
        }
        val value = placement match
          case PlacementValue.Local(v, _) => v
          case _ => raise(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString)))
        reachablePeers.foreach(n.push[S, R, V](_, key, value))
        PlacementValue.Remote(key)
      },
      onRemote = {
        // val reachablePeers = n.reachablePeersOf[S]
        // ensure(reachablePeers.nonEmpty) {
        //   NetworkError.SinglePeerExpected(s"Expected at least one reachable peer of type ${sender}, but found none")
        // }
        // val remotePeer = reachablePeers.head
        val value = n.retrieve[S, V](key)
        PlacementValue.Local(value, key)
      },
      default = PlacementValue.Remote(key)
    )
    
  def broadcast[S <: Peer: PeerTag, V](using n: Network)(placement: V on S): V =
    val sender = summon[PeerTag[S]]
    val key = placement.key
    val value = select(local, sender)(
      onLocal = placement match
        case PlacementValue.Local(v, _) => v
        case _ => raise(NetworkError.RuntimeError(LocixError.ExpectLocalValue(key, sender.toString))),
      onRemote = n.retrieve[S, V](key)
    )
    n.broadcast(key, value)
    value

object ChoreographyHandler:
  def run[P <: Peer: PeerTag](using r: Raise[NetworkError])[V](program: Choreography^ ?=> V): V =
    given (Choreography^{r}) = ChoreographyEffectImpl[P]
    program

  def handler[P <: Peer: PeerTag](using Raise[NetworkError]): Choreography = ChoreographyEffectImpl[P]
