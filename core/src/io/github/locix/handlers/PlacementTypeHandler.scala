package io.github.locix.handlers

import io.github.locix.network.{Identifier, Network, NetworkError}
import io.github.locix.peers.Peers
import io.github.locix.placement.PeerScope
import io.github.locix.placement.{PlacementType, PeerScope}
import io.github.locix.placement.PlacementType.*
import io.github.locix.peers.Peers.Peer
import io.github.locix.peers.Peers.PeerTag
import io.github.locix.network.Identifier
import io.github.locix.network.Network
import io.github.locix.network.NetworkError
import io.github.locix.raise.Raise
import io.github.locix.utils.Utils.select
import io.github.locix.signal.Signal
import scala.annotation.nowarn
import scala.reflect.ClassTag

private final class PlacementTypeHandler[L <: Peer: PeerTag] extends PlacementType:
  private var counter = -1
  update def on[P <: Peer: PeerTag, V: ClassTag](using Network)(body: PeerScope[P] ?=> V): V on P =
    val net = summon[Network]
    val local = summon[PeerTag[L]]
    val placedPeer = summon[PeerTag[P]]
    val tag = summon[ClassTag[V]]
    val key = if tag.runtimeClass.isAssignableFrom(classOf[Signal[?]]) then
      freshKey[P](Some("signal"), Map.empty)
    else
      freshKey[P](None, Map.empty)

    select(local, placedPeer)(
      onLocal = {
        given PeerScope[P] = new PeerScope[P] {
          def id: Identifier = key
        }
        val result = body
        result match
          case signal: Signal[_] =>
            net.registerSignal[V](key, signal.asInstanceOf[Signal[V]]) // Cast needed due to type erasure, but we ensure type safety by only allowing Signal[_] to be registered
          case value => net.store(key, value)
        PlacementValue.Local(result, key)
      },
      onRemote = PlacementValue.Remote(key)
    )

  override protected[locix] update def freshKey[P <: Peer](namespace: Option[String] = None, metadata: Map[String, String] = Map.empty): Identifier =
    counter += 1
    Identifier(
      id = s"locix::placement::${namespace.getOrElse("default")}::${metadata.map { case (k, v) => s"$k=$v" }.mkString(",")}::${counter}",
      namespace = namespace,
      metadata = metadata
    )

  protected[locix] def local[V, P <: Peer](value: V, key: Identifier): V on P = PlacementValue.Local(value, key)
  protected[locix] def remote[V, P <: Peer](key: Identifier): V on P = PlacementValue.Remote(key)
  protected[locix] def getKey[V, P <: Peer](value: V on P): Identifier = value.key
  protected[locix] def getLocalValue[V, P <: Peer](value: V on P): Option[V]  = value match
    case PlacementValue.Local(v, _) => Some(v)
    case _ => None

object PlacementTypeHandler:
  def run[L <: Peer: PeerTag, A](using r: Raise[NetworkError])(program: PlacementType^ ?=> A): A =
    given (PlacementType^) = PlacementTypeHandler[L]()
    program

  def handler[L <: Peer: PeerTag]: PlacementType^ = PlacementTypeHandler[L]()
