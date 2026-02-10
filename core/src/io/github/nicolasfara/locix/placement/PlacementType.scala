package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.peers.Peers.*
import PlacementType.*
import scala.caps.Mutable
import io.github.nicolasfara.locix.network.Identifier
import io.github.nicolasfara.locix.network.Network
import scala.caps.ExclusiveCapability
import scala.annotation.targetName

type Placement = PlacementType^

trait PlacementType extends Mutable:
  update def on[P <: Peer: PeerTag, V](using Network)(body: PeerScope[P] ?=> V): V on P

  protected[locix] update def freshKey[P <: Peer](namespace: Option[String] = None, metadata: Map[String, String] = Map.empty): Identifier

  protected[locix] def local[V, P <: Peer](value: V, key: Identifier): V on P
  protected[locix] def remote[V, P <: Peer](key: Identifier): V on P
  protected[locix] def getKey[V, P <: Peer](value: V on P): Identifier
  protected[locix] def getLocalValue[V, P <: Peer](value: V on P): Option[V]

object PlacementType:
  infix type on[+V, -P <: Peer] = PlacementValue[V, P]

  protected[locix] enum PlacementValue[+V, -P <: Peer](val key: Identifier):
    case Local(value: V, override val key: Identifier) extends PlacementValue[V, P](key)
    case Remote(override val key: Identifier) extends PlacementValue[V, P](key)

  def on[P <: Peer: PeerTag](using pt: PlacementType^, n: Network)[V](body: PeerScope[P] ?=> V): V on P =
    pt.on(body)
