package io.github.nicolasfara.locix.distributed

import io.github.nicolasfara.locix.network.{Network, NetworkError, Identifier}
import io.github.nicolasfara.locix.peers.Peers.{Peer, TiedWith, PeerTag}
import io.github.nicolasfara.locix.raise.Raise
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.util.Try
import scala.caps.SharedCapability
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/** In-memory network implementation simulating distributed peers communication.
  *
  * Peers interact exclusively through a shared [[NetworkBroker]] which acts as a
  * passive, static message/value store. The broker never holds references to
  * network instances — it only stores mailboxes, values, and metadata.
  * Each peer reads from its own mailbox in the broker and writes into others'.
  */
private final class InMemoryNetworkImpl[LocalPeer <: Peer: PeerTag](
  private val address: String,
  private val broker: NetworkBroker
) extends Network:
  
  type PeerAddress = String
  type KeyId = String

  // Signal subscriptions: signalId -> list of callbacks (local only)
  // Using Any to avoid capture checking issues with function types
  private val signalSubscriptions = TrieMap[Identifier, mutable.ListBuffer[Any]]()

  override def reachablePeers: Set[PeerAddress] = 
    broker.getAllPeers.filterNot(_ == address)

  override def reachablePeersOf[P <: Peer: PeerTag]: Set[PeerAddress] =
    val targetPeerTag = summon[PeerTag[P]]
    broker.getPeersOfType(targetPeerTag.baseTypeRepr).filterNot(_ == address)

  override def peerAddress: PeerAddress = address

  override def push[S <: TiedWith[D], D <: Peer, V](using r: Raise[NetworkError])(
    to: PeerAddress,
    key: Identifier,
    value: V
  ): Unit =
    if !broker.hasPeer(to) then
      r.raise(NetworkError.UnreachablePeer(s"Peer $to is not reachable"))
    broker.putValue(to, key, value)

  override def pull[From <: TiedWith[To], To <: Peer, V](using r: Raise[NetworkError])(
    from: PeerAddress,
    key: Identifier
  ): V =
    if !broker.hasPeer(from) then
      r.raise(NetworkError.UnreachablePeer(s"Cannot reach peer $from"))
    // Poll the broker for the value deposited into this peer's store (by the remote peer via push)
    val maxRetries = 10
    val baseDelay = 50.millis

    def tryPull(attempt: Int): V =
      broker.getValue(address, key) match
        case Some(value) => value.asInstanceOf[V]
        case None if attempt >= maxRetries =>
          r.raise(NetworkError.NetworkFailure(s"Pull from $from for key $key timed out"))
        case None =>
          Thread.sleep(baseDelay.toMillis * Math.pow(1.5, attempt).toLong)
          tryPull(attempt + 1)

    tryPull(0)

  override def broadcast[S <: Peer, V](using r: Raise[NetworkError])(
    key: Identifier,
    value: V
  ): Unit =
    broker.getAllPeers.foreach { peer =>
      broker.putValue(peer, key, value)
    }

  override def retrieve[S <: Peer, V](using r: Raise[NetworkError])(
    key: Identifier
  ): V =
    // Look up value in this peer's store in the broker
    val maxRetries = 10
    val baseDelay = 50.millis

    def tryRetrieve(attempt: Int): V =
      broker.getValue(address, key) match
        case Some(value) => value.asInstanceOf[V]
        case None if attempt >= maxRetries =>
          r.raise(NetworkError.KeyNotFound(key))
        case None =>
          Thread.sleep(baseDelay.toMillis * Math.pow(1.5, attempt).toLong)
          tryRetrieve(attempt + 1)

    tryRetrieve(0)

  override def store[V](key: Identifier, value: V): Unit =
    broker.putValue(address, key, value)

  // ---- Reactive primitives ----

  override def emit[V](key: Identifier, value: V): Unit =
    // Emit to local subscribers
    signalSubscriptions.get(key).foreach { callbacks =>
      callbacks.foreach { cb =>
        Try(cb.asInstanceOf[V => Unit](value))
      }
    }
    // Put the emission into the broker's signal mailboxes for remote subscribers
    broker.emitSignal(address, key, value)

  override def close(key: Identifier): Unit =
    signalSubscriptions.remove(key)
    broker.closeSignal(key)

  override def subscribe[V](to: PeerAddress, signalId: Identifier, callback: V => Unit): Unit =
    val callbacks = signalSubscriptions.getOrElseUpdate(
      signalId,
      mutable.ListBuffer[Any]()
    )
    callbacks.addOne(callback.asInstanceOf[Any])
    if to != address then
      broker.registerSubscription(to, signalId, address)

  override def unsubscribe(to: PeerAddress, signalId: Identifier): Unit =
    signalSubscriptions.remove(signalId)
    if to != address then
      broker.unregisterSubscription(to, signalId, address)

  /** Drain pending signal emissions from the broker and invoke local callbacks.
    * This should be called periodically (or in an event-loop) to process
    * remote signal emissions that were deposited into this peer's mailbox.
    */
  def drainSignals(): Unit =
    val pending = broker.drainSignalMailbox(address)
    pending.foreach { case (signalId, value) =>
      signalSubscriptions.get(signalId).foreach { callbacks =>
        callbacks.foreach { cb =>
          Try(cb.asInstanceOf[Any => Unit](value))
        }
      }
    }

/** A static, passive message broker that stores values and signal emissions.
  *
  * The broker has '''no references''' to any network instance. It only maintains:
  * - Per-peer key-value stores (mailboxes for placed values)
  * - Peer type metadata for discovery
  * - Signal subscription metadata and emission mailboxes
  *
  * Peers write into the broker and read from it — the broker never pushes.
  */
class NetworkBroker:
  // Per-peer value stores: peerAddress -> (key -> value)
  private val peerStores: TrieMap[String, TrieMap[Identifier, Any]] = TrieMap.empty

  // Peer type metadata: peerAddress -> peerTypeName
  private val peerTypes: TrieMap[String, String] = TrieMap.empty

  // Signal subscriptions: signalId -> set of subscriber peer addresses
  private val signalSubscribers: TrieMap[Identifier, mutable.Set[String]] = TrieMap.empty

  // Signal emission mailboxes: subscriberAddress -> queue of (signalId, value)
  private val signalMailboxes: TrieMap[String, LinkedBlockingQueue[(Identifier, Any)]] = TrieMap.empty

  /** Register a peer address with its type. No network reference is stored. */
  def registerPeer[P <: Peer: PeerTag](address: String): Unit =
    peerStores.getOrElseUpdate(address, TrieMap.empty)
    signalMailboxes.getOrElseUpdate(address, LinkedBlockingQueue())
    peerTypes.put(address, summon[PeerTag[P]].baseTypeRepr)

  /** Unregister a peer, removing all its stored data. */
  def unregisterPeer(address: String): Unit =
    peerStores.remove(address)
    peerTypes.remove(address)
    signalMailboxes.remove(address)

  /** Check whether a peer is registered. */
  def hasPeer(address: String): Boolean =
    peerStores.contains(address)

  /** Get all registered peer addresses. */
  def getAllPeers: Set[String] =
    peerStores.keySet.toSet

  /** Get all peer addresses registered with the given type name. */
  def getPeersOfType(peerType: String): Set[String] =
    peerTypes.filter(_._2 == peerType).keySet.toSet

  /** Store a value in a peer's mailbox, keyed by [[Identifier]]. */
  def putValue(peerAddress: String, key: Identifier, value: Any): Unit =
    peerStores.getOrElseUpdate(peerAddress, TrieMap.empty).put(key, value)

  /** Retrieve a value from a peer's store. Returns [[None]] if not yet available. */
  def getValue(peerAddress: String, key: Identifier): Option[Any] =
    peerStores.get(peerAddress).flatMap(_.get(key))

  // ---- Signal helpers ----

  /** Register that [[subscriber]] wants to receive emissions for [[signalId]] from [[signalSource]]. */
  def registerSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
    signalSubscribers.getOrElseUpdate(signalId, mutable.Set.empty).add(subscriber)

  /** Unregister a signal subscription. */
  def unregisterSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
    signalSubscribers.get(signalId).foreach(_.remove(subscriber))

  /** Deposit a signal emission into all subscribers' mailboxes. */
  def emitSignal(from: String, signalId: Identifier, value: Any): Unit =
    signalSubscribers.get(signalId).foreach { subscribers =>
      subscribers.foreach { subscriber =>
        if subscriber != from then
          signalMailboxes.getOrElseUpdate(subscriber, LinkedBlockingQueue())
            .put((signalId, value))
      }
    }

  /** Remove all subscriptions for a signal. */
  def closeSignal(signalId: Identifier): Unit =
    signalSubscribers.remove(signalId)

  /** Drain all pending signal emissions for a peer. */
  def drainSignalMailbox(peerAddress: String): List[(Identifier, Any)] =
    signalMailboxes.get(peerAddress) match
      case Some(queue) =>
        val buffer = mutable.ListBuffer[(Identifier, Any)]()
        var item: (Identifier, Any) | Null = queue.poll()
        while item != null do
          buffer.addOne(item.nn)
          item = queue.poll()
        buffer.toList
      case None => Nil

object InMemoryNetwork:
  /** Create a network for simulating multiple distributed peers.
    *
    * @param address the unique address identifying this peer
    * @param broker the shared (static) broker used as a passive message store
    * @return a network instance for the given peer
    */
  def apply[P <: Peer: PeerTag](address: String, broker: NetworkBroker): Network =
    broker.registerPeer[P](address)
    InMemoryNetworkImpl[P](address, broker)

  /** Create a network broker for coordinating multiple peers. */
  def broker(): NetworkBroker = NetworkBroker()

  /** Convenience method to create a network with a new broker. */
  def standalone[P <: Peer: PeerTag](address: String): (Network, NetworkBroker) =
    val b = broker()
    val net = apply[P](address, b)
    (net, b)
