package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Identifier
import scala.caps.SharedCapability
import scala.concurrent.ExecutionContext
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import java.util.concurrent.{CopyOnWriteArrayList, CountDownLatch}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import io.github.nicolasfara.locix.peers.Peers.peer

/** A reactive signal placed on peer [[Local]], carrying values of type [[V]].
  *
  * Signals are the fundamental reactive primitive in the multitier architecture:
  * they allow a peer to [[emit]] values and other peers to [[subscribe]] to changes.
  */
trait Signal[V, Local <: Peer](val id: Identifier) extends SharedCapability:
  /** Emit a value to all subscribers of this signal. */
  def emit(using Network, PeerScope[Local])(value: V): Unit

  /** Subscribe to values emitted by this signal. */
  def subscribe[P <: Peer](using Network, PeerScope[P])(callback: V => Unit): Unit

  /** Close this signal, unsubscribing all listeners. */
  def close(using Network, PeerScope[Local]): Unit

  /** Derive a new signal by transforming each emitted value with [[f]]. */
  def map[U, P <: Peer: PeerTag](using Network, PeerScope[P])(f: V -> U): Signal[U, P]

  /** Derive a new signal that only propagates values satisfying [[p]]. */
  def filter[P <: Peer: PeerTag](using Network, PeerScope[P])(p: V -> Boolean): Signal[V, P]

  /** Accumulate a running state over emitted values.
    *
    * Each time a value `v` is emitted, the state is updated via `f(state, v)`
    * and the new state is emitted on the derived signal.
    *
    * @param initial the initial accumulator value (not emitted)
    * @param f       the folding function `(accumulator, value) => newAccumulator`
    */
  def fold[U, P <: Peer: PeerTag](using Network, PeerScope[P])(initial: U)(f: (U, V) -> U): Signal[U, P]

  /** Emit only the first [[n]] values, then automatically close the derived signal. */
  def take[P <: Peer: PeerTag](using Network, PeerScope[P])(n: Int): Signal[V, P]

  /** Skip the first [[n]] values, then propagate all subsequent values. */
  def drop[P <: Peer: PeerTag](using Network, PeerScope[P])(n: Int): Signal[V, P]

  /** Convenience: subscribe with a side-effecting action. */
  def foreach[P <: Peer](using Network, PeerScope[P])(action: V => Unit): Unit

  /** Block and collect all emitted values until the signal is [[close closed]].
    *
    * This is a blocking operation: the calling thread will park until the
    * producing peer calls [[close]] on this signal, at which point all
    * collected values are returned in emission order.
    *
    * @return the collected values in emission order
    */
  def collectValues[P <: Peer](using Network, PeerScope[P]): List[V]

object Signal:
  /** Create a signal placed on peer [[L]] and immediately emit [[initialValue]]. */
  def apply[V](initialValue: V)[L <: Peer: PeerTag as peerTag](using scope: PeerScope[L]^, net: Network): Signal[V, L]^{scope} =
    val signal = SignalImpl[V, L](peerTag, scope.id, CountDownLatch(1))
    net.emit(scope.id, initialValue)
    signal

  /** Create a signal and execute [[body]] asynchronously to produce values.
    *
    * Exceptions thrown inside [[body]] are reported to the [[ExecutionContext]]'s reporter.
    */
  def signalling[V, L <: Peer: PeerTag as peerTag](using scope: PeerScope[L]^, ec: ExecutionContext^)(body: Signal[V, L] => Unit): Signal[V, L]^{scope} =
    val signal = SignalImpl[V, L](peerTag, scope.id, CountDownLatch(1))
    ec.execute: () =>
      try body(signal)
      catch case ex: Exception => ec.reportFailure(ex)
    signal

  // /** Merge two signals into one that emits values from both.
  //   *
  //   * The merged signal closes when ''both'' source signals have been closed.
  //   */
  // def merge[V, L <: Peer, P <: Peer: PeerTag as peerTag](using net: Network, scope: PeerScope[P])(
  //   s1: Signal[V, L],
  //   s2: Signal[V, L],
  // ): Signal[V, L] =
  //   val mergedId = derivedId(s1.id, s2.id, "merge")
  //   val mergedLatch = CountDownLatch(1)
  //   val merged = SignalImpl[V, L](peerTag, mergedId, mergedLatch)
  //   val remaining = AtomicInteger(2)
  //   def propagate(v: V): Unit = net.emit(mergedId, v)
  //   net.subscribe[V](s1.id, propagate)
  //   net.subscribe[V](s2.id, propagate)
  //   // Close merged only when both sources are closed.
  //   asImpl(s1).registerChild(mergedLatch, remaining)
  //   asImpl(s2).registerChild(mergedLatch, remaining)
  //   merged

  // /** Combine the latest values from two signals into pairs.
  //   *
  //   * Emits a `(V1, V2)` every time ''either'' source emits, once both have
  //   * emitted at least one value. Closes when ''both'' sources are closed.
  //   */
  // def zip[V1, V2, L <: Peer, P <: Peer](using net: Network, scope: PeerScope[P])(
  //   s1: Signal[V1, L],
  //   s2: Signal[V2, L],
  // ): Signal[(V1, V2), L] =
  //   val zippedId = derivedId(s1.id, s2.id, "zip")
  //   val zippedLatch = CountDownLatch(1)
  //   val zipped = SignalImpl[(V1, V2), L](zippedId, zippedLatch)
  //   val latestLeft = AtomicReference[Option[V1]](None)
  //   val latestRight = AtomicReference[Option[V2]](None)
  //   net.subscribe[V1](s1.id, v1 =>
  //     latestLeft.set(Some(v1))
  //     latestRight.get().foreach(v2 => net.emit(zippedId, (v1, v2)))
  //   )
  //   net.subscribe[V2](s2.id, v2 =>
  //     latestRight.set(Some(v2))
  //     latestLeft.get().foreach(v1 => net.emit(zippedId, (v1, v2)))
  //   )
  //   val remaining = AtomicInteger(2)
  //   asImpl(s1).registerChild(zippedLatch, remaining)
  //   asImpl(s2).registerChild(zippedLatch, remaining)
  //   zipped

  // -- internal helpers ------------------------------------------------------

  private def derivedId(parent: Identifier, suffix: String): Identifier =
    Identifier(s"${parent.id}::$suffix", parent.namespace, parent.metadata)

  private def derivedId(left: Identifier, right: Identifier, op: String): Identifier =
    Identifier(s"${left.id}::$op::${right.id}", left.namespace, left.metadata ++ right.metadata)

  private def asImpl[V, L <: Peer](signal: Signal[V, L]): SignalImpl[V, L] =
    signal.asInstanceOf[SignalImpl[V, L]]

  private class SignalImpl[V, L <: Peer](upstreamTag: PeerTag[L], id: Identifier, closeLatch: CountDownLatch) extends Signal[V, L](id):
    // Tracks child latches that should be counted down on close.
    // Each entry is (latch, remaining counter). The latch is counted down only
    // when the counter reaches zero, supporting multi-parent scenarios (merge, zip).
    private val childLatches = AtomicReference[List[(CountDownLatch, AtomicInteger)]](Nil)

    private[Signal] def notifyClose(): Unit =
      closeLatch.countDown()
      childLatches.get().foreach: (latch, remaining) =>
        if remaining.decrementAndGet() <= 0 then latch.countDown()

    /** Register a child latch counted down when this signal closes.
      * For single-parent derived signals, pass a counter initialised to 1.
      */
    private[Signal] def registerChild(child: CountDownLatch, remaining: AtomicInteger = AtomicInteger(1)): Unit =
      childLatches.updateAndGet((child, remaining) :: _)
      // If already closed, fire immediately.
      if closeLatch.getCount == 0 then
        if remaining.decrementAndGet() <= 0 then child.countDown()

    override def emit(using net: Network, scope: PeerScope[L])(value: V): Unit =
      net.emit(scope.id, value)

    override def subscribe[P <: Peer](using net: Network, scope: PeerScope[P])(callback: V => Unit): Unit =
      val remotePeers = net.reachablePeersOf[L]
      remotePeers.foreach(net.subscribe(_, id, callback))

    override def close(using net: Network, scope: PeerScope[L]): Unit =
      net.close(id)
      notifyClose()

    override def map[U, P <: Peer: PeerTag as downstreamTag](using net: Network, scope: PeerScope[P])(f: V -> U): Signal[U, P] =
      val childId = derivedId(id, "map")
      val childLatch = CountDownLatch(1)
      val mapped = SignalImpl[U, P](downstreamTag, childId, childLatch)
      net.reachablePeersOf[L].foreach(net.subscribe[V](_, id, v => net.emit(childId, f(v))))
      registerChild(childLatch)
      mapped

    override def filter[P <: Peer: PeerTag as downstreamTag](using net: Network, scope: PeerScope[P])(p: V -> Boolean): Signal[V, P] =
      val childId = derivedId(id, "filter")
      val childLatch = CountDownLatch(1)
      val filtered = SignalImpl[V, P](downstreamTag, childId, childLatch)
      net.reachablePeersOf[L].foreach(net.subscribe[V](_, id, v => if p(v) then net.emit(childId, v)))
      registerChild(childLatch)
      filtered

    override def fold[U, P <: Peer: PeerTag as downstreamTag](using net: Network, scope: PeerScope[P])(initial: U)(f: (U, V) -> U): Signal[U, P] =
      val childId = derivedId(id, "fold")
      val childLatch = CountDownLatch(1)
      val folded = SignalImpl[U, P](downstreamTag, childId, childLatch)
      val state = AtomicReference[U](initial)
      net.reachablePeersOf[L].foreach(net.subscribe[V](_, id, v =>
        val newState = f(state.get(), v)
        state.set(newState)
        net.emit(childId, newState)
      ))
      registerChild(childLatch)
      folded

    override def take[P <: Peer: PeerTag as downstreamTag](using net: Network, scope: PeerScope[P])(n: Int): Signal[V, P] =
      val childId = derivedId(id, "take")
      val childLatch = CountDownLatch(1)
      val taken = SignalImpl[V, P](downstreamTag, childId, childLatch)
      val remaining = AtomicInteger(n)
      net.reachablePeersOf[L].foreach(net.subscribe[V](_, id, v =>
        val left = remaining.getAndDecrement()
        if left > 0 then
          net.emit(childId, v)
          if left == 1 then taken.notifyClose()
      ))
      registerChild(childLatch)
      taken

    override def drop[P <: Peer: PeerTag as downstreamTag](using net: Network, scope: PeerScope[P])(n: Int): Signal[V, P] =
      val childId = derivedId(id, "drop")
      val childLatch = CountDownLatch(1)
      val dropped = SignalImpl[V, P](downstreamTag, childId, childLatch)
      val toDrop = AtomicInteger(n)
      net.reachablePeersOf[L].foreach(net.subscribe[V](_, id, v =>
        if toDrop.get() <= 0 then net.emit(childId, v)
        else toDrop.decrementAndGet()
      ))
      registerChild(childLatch)
      dropped

    override def foreach[P <: Peer](using net: Network, scope: PeerScope[P])(action: V => Unit): Unit =
      subscribe(action)

    override def collectValues[P <: Peer](using net: Network, scope: PeerScope[P]): List[V] =
      val buffer = CopyOnWriteArrayList[V]()
      subscribe: v =>
        buffer.add(v)
      closeLatch.await()
      buffer.asScala.toList

    override def toString: String = s"Signal($id)"
    override def hashCode: Int = id.hashCode
    override def equals(that: Any): Boolean = that match
      case s: Signal[?, ?] => id == s.id
      case _               => false
