package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Identifier
import scala.caps.SharedCapability
import java.util.concurrent.atomic.AtomicLong


trait Signal[V] extends SharedCapability:
  def id: Identifier
  def notify(using Network)(value: V): Unit
  def subscribe(using Network)(subscriberId: Identifier, callback: V => Unit): Unit
  def close(using Network): Unit

object Signal:
  private val counter = AtomicLong(0L)

  private def freshId(base: Identifier): Identifier = new Identifier:
    override val id: String = s"${base.id}::mapped::${counter.getAndIncrement()}"
    override val namespace: Option[String] = base.namespace
    override val metadata: Map[String, String] = base.metadata

  def apply[P <: Peer, V](using n: Network, p: PlacementType, s: PeerScope[P])(): Signal[V] =
    new SignalImpl(s.id)

  private class SignalImpl[V](signalId: Identifier)(using n: Network) extends Signal[V]:
    override def id: Identifier = signalId

    override def notify(using net: Network)(value: V): Unit =
      net.propagate(signalId, value)

    override def subscribe(using net: Network)(subscriberId: Identifier, callback: V => Unit): Unit =
      net.subscribe[V](signalId, subscriberId, (_, v) => callback(v))

    override def close(using net: Network): Unit =
      net.close(signalId)

  def mapped[V, U](source: Signal[V], f: V -> U)(using n: Network): Signal[U] =
    val derivedId = freshId(source.id)
    val derived = new SignalImpl[U](derivedId)
    source.subscribe(using n)(derivedId, v => derived.notify(using n)(f(v)))
    derived
