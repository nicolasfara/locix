package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.placement.{PlacementType, PeerScope}
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.network.Identifier
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.utils.Utils.select
import io.github.nicolasfara.locix.placement.Signal
import scala.reflect.Typeable
import scala.collection.IndexedSeqView.Id

private final class PlacementTypeHandler[L <: Peer: PeerTag] extends PlacementType:
  private var counter = 0
  update def on[P <: Peer: PeerTag, V](using Network)(body: PeerScope[P] ?=> V): V on P =
    val net = summon[Network]
    val local = summon[PeerTag[L]]
    val placedPeer = summon[PeerTag[P]]
    val key = freshKey[P](None, Map.empty)
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

  override protected[locix] update def freshKey[P <: Peer](namespace: Option[String] = None, metadata: Map[String, String] = Map.empty): Identifier =
    counter += 1
    new Identifier:
      override val id: String = s"locix::placement::${namespace.getOrElse("default")}::${metadata.map { case (k, v) => s"$k=$v" }.mkString(",")}::${counter}"
      override val namespace: Option[String] = namespace
      override val metadata: Map[String, String] = metadata

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
	
