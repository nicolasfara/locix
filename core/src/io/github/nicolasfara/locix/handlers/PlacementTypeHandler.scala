package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.placement.{PlacementType}
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.network.Identifier
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.utils.Utils.select
import cats.instances.map
import io.github.nicolasfara.locix.placement.Signal

private final class PlacementTypeHandler[L <: Peer: PeerTag] extends PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placement[V, P]

  protected[locix] enum Placement[+V, -P <: Peer](val key: Identifier):
    case Local(value: V, override val key: Identifier) extends Placement[V, P](key)
    case Remote(override val key: Identifier) extends Placement[V, P](key)

  def take[P <: Peer, V](using ps: PeerScope[P])(placement: V on P): V =
    val Placement.Local(value, _) = placement.runtimeChecked
    value
  def on[P <: Peer: PeerTag, V](using OnGuard^, Network)(body: PeerScope[P] ?=> V): V on P =
    val net = summon[Network]
    val local = summon[PeerTag[L]]
    val placedPeer = summon[PeerTag[P]]
    val key = net.createKey(None, Map.empty)
    select(local, placedPeer)(
      onLocal = {
        given PeerScope[P] = new PeerScope[P] {
          def id: Identifier = key
        }
        val result = body
        net.store(key, result)
        Placement.Local(result, key)
      },
      onRemote = Placement.Remote(key)
    )

  protected[locix] def local[V, P <: Peer](value: V, key: Identifier): V on P = Placement.Local(value, key)
  protected[locix] def remote[V, P <: Peer](key: Identifier): V on P = Placement.Remote(key)
  protected[locix] def getKey[V, P <: Peer](value: V on P): Identifier = value.key
  protected[locix] def getLocalValue[V, P <: Peer](value: V on P): Option[V]  = value match
    case Placement.Local(v, _) => Some(v)
    case _ => None

object PlacementTypeHandler:
  def run[L <: Peer: PeerTag, A](using r: Raise[NetworkError])(program: PlacementType ?=> A): A =
    given PlacementType = PlacementTypeHandler[L]()
    program
	
