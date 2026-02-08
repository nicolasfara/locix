package io.github.nicolasfara.locix.network

import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.peers.Peers.*
import scala.caps.SharedCapability

enum NetworkError:
  case SinglePeerExpected(peerType: String)
  case UnreachablePeer(info: String)
  case NetworkFailure(message: String)
  case RuntimeError[E](error: E)

trait Network extends SharedCapability:
  type PeerAddress
  type KeyId

  def reachablePeers: Set[PeerAddress]

  def reachablePeersOf[P <: Peer]: Set[PeerAddress]

  def push[S <: TiedWith[D], D <: Peer, V](using Raise[NetworkError])(to: PeerAddress, key: Identifier, value: V): Unit
  def pull[From <: TiedWith[To], To <: Peer, V](using Raise[NetworkError])(from: PeerAddress, key: Identifier): V
  def broadcast[S <: Peer, V](using Raise[NetworkError])(key: Identifier, value: V): Unit
  def retrieve[S <: Peer, V](using Raise[NetworkError])(key: Identifier): V
  def store[V](key: Identifier, value: V): Unit
  def emit[V](key: Identifier, value: V): Unit
  def close(key: Identifier): Unit

  def createKey[P <: Peer](namespace: Option[String] = None, metadata: Map[String, String] = Map.empty): Identifier
  def peerAddress: PeerAddress
