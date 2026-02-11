package io.github.nicolasfara.locix.distributed

import io.github.nicolasfara.locix.network.{ Identifier, Network, NetworkError }
import io.github.nicolasfara.locix.peers.Peers.{ Peer, PeerTag, TiedWith }
import io.github.nicolasfara.locix.raise.Raise
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.util.Try
import scala.caps.SharedCapability
import java.util.UUID
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import io.github.nicolasfara.locix.raise.Raise.raise
import io.github.nicolasfara.locix.raise.Raise.ensure
import scala.annotation.tailrec
import java.util.concurrent.CountDownLatch
import scala.concurrent.Promise
import scala.concurrent.Await
import java.util.concurrent.TimeoutException

/**
 * In-memory network implementation simulating distributed peers communication.
 *
 * Peers interact exclusively through a shared [[NetworkBroker]] which acts as a passive, static message/value store. The broker never holds
 * references to network instances — it only stores mailboxes, values, and metadata. Each peer reads from its own mailbox in the broker and writes
 * into others'.
 */
private final class InMemoryNetworkImpl[LocalPeer <: Peer: PeerTag](
    private val address: String,
    private val broker: NetworkBroker,
    private val timeout: FiniteDuration = 5.seconds,
) extends Network:

  type PeerAddress = String
  type KeyId = String

  // Signal subscriptions: signalId -> list of callbacks (local only)
  // Using Any to avoid capture checking issues with function types
  private val signalSubscriptions = TrieMap[Identifier, mutable.ListBuffer[Any]]()

  override def reachablePeers: Set[PeerAddress] = broker.getAllPeers.filterNot(_ == address)

  override def reachablePeersOf[P <: Peer: PeerTag]: Set[PeerAddress] =
    val targetPeerTag = summon[PeerTag[P]]
    broker.getPeersOfType(targetPeerTag.baseTypeRepr).filterNot(_ == address)

  override def peerAddress: PeerAddress = address

  override def push[S <: TiedWith[D], D <: Peer, V](using Raise[NetworkError])(
      to: PeerAddress,
      key: Identifier,
      value: V,
  ): Unit =
    ensure(broker.hasPeer(to)) { NetworkError.UnreachablePeer(s"Cannot reach peer $to") }
    broker.putValue(to, key, value)

  override def pull[From <: TiedWith[To], To <: Peer, V](using Raise[NetworkError])(
      from: PeerAddress,
      key: Identifier,
  ): V =
    ensure(broker.hasPeer(from)) { NetworkError.UnreachablePeer(s"Cannot reach peer $from") }
    // Request-response pattern: send a request to the remote peer and wait for the response
    val promiseResult = Promise[Any]()
    broker.putPendingRequest(from, key, promiseResult)
    val future = promiseResult.future
    Raise.catchNonFatal[NetworkError, V] {
      Await.result(future, timeout).asInstanceOf[V]
    }(err => NetworkError.NetworkFailure(s"Failed to pull value from $from for key $key: ${err.getMessage}"))

  override def pullFromAll[From <: TiedWith[To], To <: Peer, V](using Raise[NetworkError])(from: Set[String], key: Identifier): Map[String, V] =
    ensure(from.nonEmpty) { NetworkError.UnreachablePeer("No peer addresses provided for pullFromAll") }
    from.map { peerAddress =>
      ensure(broker.hasPeer(peerAddress)) { NetworkError.UnreachablePeer(s"Cannot reach peer $peerAddress") }
      val promiseResult = Promise[Any]()
      broker.putPendingRequest(peerAddress, key, promiseResult)
      val future = promiseResult.future
      val value = Raise.catchNonFatal[NetworkError, V] {
        Await.result(future, timeout).asInstanceOf[V]
      }(err => NetworkError.NetworkFailure(s"Failed to pull value from $peerAddress for key $key: ${err.getMessage}"))
      peerAddress -> value
    }.toMap    

  override def broadcast[S <: Peer, V](using Raise[NetworkError])(key: Identifier, value: V): Unit =
    broker.getAllPeers.foreach(broker.putValue(_, key, value))

  override def retrieve[S <: Peer, V](using Raise[NetworkError])(key: Identifier): V =
    // Look up value in this peer's store in the broker
    val maxRetries = 10
    val baseDelay = 50.millis

    @tailrec def tryRetrieve(attempt: Int): V =
      broker.getValue(address, key) match
        case Some(value) => value.asInstanceOf[V]
        case None if attempt >= maxRetries => raise(NetworkError.KeyNotFound(key))
        case None =>
          Thread.sleep(baseDelay.toMillis * Math.pow(1.5, attempt).toLong)
          tryRetrieve(attempt + 1)

    tryRetrieve(0)

  override def store[V](key: Identifier, value: V): Unit = broker.putValue(address, key, value)

  // ---- Reactive primitives ----

  override def emit[V](key: Identifier, value: V): Unit = ???
    // // Emit to local subscribers
    // signalSubscriptions.get(key).foreach { callbacks =>
    //   callbacks.foreach { cb =>
    //     Try(cb.asInstanceOf[V => Unit](value))
    //   }
    // }
    // // Put the emission into the broker's signal mailboxes for remote subscribers
    // broker.emitSignal(address, key, value)

  override def close(key: Identifier): Unit = ???
    // signalSubscriptions.remove(key)
    // broker.closeSignal(key)

  override def subscribe[V](to: PeerAddress, signalId: Identifier, callback: V => Unit): Unit = ???
    // val callbacks = signalSubscriptions.getOrElseUpdate(
    //   signalId,
    //   mutable.ListBuffer[Any](),
    // )
    // callbacks.addOne(callback.asInstanceOf[Any])
    // if to != address then broker.registerSubscription(to, signalId, address)

  override def unsubscribe(to: PeerAddress, signalId: Identifier): Unit = ???
    // signalSubscriptions.remove(signalId)
    // if to != address then broker.unregisterSubscription(to, signalId, address)

  // ---- Request-response processing ----

  // // Background thread flag
  // private val running = AtomicBoolean(true)

  // /**
  //  * Process incoming pull requests: look up values in the local store via the broker and post responses back to the requesting peer.
  //  */
  // private[distributed] def processIncomingRequests(): Unit =
  //   val requests = broker.drainRequests(address)
  //   requests.foreach { case (requester, key, correlationId) =>
  //     broker.getValue(address, key) match
  //       case Some(value) =>
  //         broker.postResponse(requester, correlationId, value)
  //       case None =>
  //         // Value not yet available — re-enqueue the request so it can be retried
  //         broker.postRequest(address, requester, key, correlationId)
  //   }

  // // Start a background daemon thread that continuously processes incoming requests and signal emissions
  // private val requestProcessorThread: Thread =
  //   val t = Thread(() =>
  //     while running.get() do
  //       try
  //         processIncomingRequests()
  //         drainSignals()
  //         Thread.sleep(10) // Small sleep to avoid busy-waiting
  //       catch case _: InterruptedException => (),
  //   )
  //   t.setDaemon(true)
  //   t.setName(s"locix-request-processor-$address")
  //   t.start()
  //   t

  // /** Stop the background request processing thread. */
  // def shutdown(): Unit =
  //   running.set(false)
  //   requestProcessorThread.interrupt()

  // /**
  //  * Drain pending signal emissions from the broker and invoke local callbacks. This should be called periodically (or in an event-loop) to process
  //  * remote signal emissions that were deposited into this peer's mailbox.
  //  */
  // def drainSignals(): Unit =
  //   val pending = broker.drainSignalMailbox(address)
  //   pending.foreach { case (signalId, value) =>
  //     signalSubscriptions.get(signalId).foreach { callbacks =>
  //       callbacks.foreach { cb =>
  //         Try(cb.asInstanceOf[Any => Unit](value))
  //       }
  //     }
  //   }
end InMemoryNetworkImpl

/**
 * A static, passive message broker that stores values and signal emissions.
 *
 * The broker has '''no references''' to any network instance. It only maintains:
 *   - Per-peer key-value stores (mailboxes for placed values)
 *   - Peer type metadata for discovery
 *   - Signal subscription metadata and emission mailboxes
 *
 * Peers write into the broker and read from it — the broker never pushes.
 */
class NetworkBroker:
  type PeerAddress = String
  // Per-peer value stores: peerAddress -> (key -> value)
  private val peerStores: TrieMap[String, TrieMap[Identifier, Any]] = TrieMap.empty

  // Peer type metadata: peerAddress -> peerTypeName
  private val peerTypes: TrieMap[String, String] = TrieMap.empty


  // Pending requests: we use a synchronized wrapper object as both the queue holder and the lock
  private case class PendingRequestQueue(queue: LinkedBlockingQueue[Promise[Any]] = LinkedBlockingQueue())
  private val pendingRequests: TrieMap[(PeerAddress, Identifier), PendingRequestQueue] = TrieMap.empty
  
  private def getRequestQueue(peerAddress: PeerAddress, key: Identifier): PendingRequestQueue =
    pendingRequests.getOrElseUpdate((peerAddress, key), PendingRequestQueue())

  /** Register a peer address with its type. No network reference is stored. */
  def registerPeer[P <: Peer: PeerTag](address: String): Unit =
    peerStores.getOrElseUpdate(address, TrieMap.empty)
    peerTypes.put(address, summon[PeerTag[P]].baseTypeRepr)

  /** Unregister a peer, removing all its stored data. */
  def unregisterPeer(address: String): Unit =
    peerStores.remove(address)
    peerTypes.remove(address)

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
    val requestQueue = getRequestQueue(peerAddress, key)
    requestQueue.synchronized:
      // Store the value first
      peerStores.getOrElseUpdate(peerAddress, TrieMap.empty).put(key, value)
      // Then resolve any pending promises
      val promises = Iterator.continually(requestQueue.queue.poll()).takeWhile(_ != null).toList
      promises.foreach(_.success(value))
      if promises.nonEmpty then pendingRequests.remove((peerAddress, key))

  /** Retrieve a value from a peer's store. Returns [[None]] if not yet available. */
  def getValue(peerAddress: String, key: Identifier): Option[Any] =
    peerStores.get(peerAddress).flatMap(_.get(key))

  /** Store a pending request for a value that is not yet available. The promise will be completed when the value is put into the store. */
  def putPendingRequest(peerAddress: String, key: Identifier, promise: Promise[Any]): Unit =
    val requestQueue = getRequestQueue(peerAddress, key)
    requestQueue.synchronized:
      getValue(peerAddress, key) match
        case Some(value) => promise.success(value)
        case None => requestQueue.queue.put(promise)
  
  // ---- Request-response helpers ----

  // /** Post a pull request into the target peer's request mailbox. */
  // def postRequest(targetPeer: String, requester: String, key: Identifier, correlationId: String): Unit =
  //   requestMailboxes
  //     .getOrElseUpdate(targetPeer, LinkedBlockingQueue())
  //     .put((requester, key, correlationId))

  // /** Drain all pending pull requests for a peer. Returns a list of (requesterAddress, key, correlationId). */
  // def drainRequests(peerAddress: String): List[(String, Identifier, String)] =
  //   requestMailboxes.get(peerAddress) match
  //     case Some(queue) =>
  //       val buffer = mutable.ListBuffer[(String, Identifier, String)]()
  //       var item: (String, Identifier, String) | Null = queue.poll()
  //       while item != null do
  //         buffer.addOne(item.nn)
  //         item = queue.poll()
  //       buffer.toList
  //     case None => Nil

  // /** Post a response value for a specific correlationId into the requester's response mailbox. */
  // def postResponse(requester: String, correlationId: String, value: Any): Unit =
  //   responseMailboxes
  //     .getOrElseUpdate(requester, TrieMap.empty)
  //     .put(correlationId, value)

  // /** Poll for a response matching the given correlationId. Returns [[Some]] if available, consuming the entry. */
  // def pollResponse(peerAddress: String, correlationId: String): Option[Any] =
  //   responseMailboxes.get(peerAddress).flatMap(_.remove(correlationId))

  // // ---- Signal helpers ----

  // /** Register that [[subscriber]] wants to receive emissions for [[signalId]] from [[signalSource]]. */
  // def registerSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
  //   signalSubscribers.getOrElseUpdate(signalId, mutable.Set.empty).add(subscriber)

  // /** Unregister a signal subscription. */
  // def unregisterSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
  //   signalSubscribers.get(signalId).foreach(_.remove(subscriber))

  // /** Deposit a signal emission into all subscribers' mailboxes. */
  // def emitSignal(from: String, signalId: Identifier, value: Any): Unit =
  //   signalSubscribers.get(signalId).foreach { subscribers =>
  //     subscribers.foreach { subscriber =>
  //       if subscriber != from then
  //         signalMailboxes
  //           .getOrElseUpdate(subscriber, LinkedBlockingQueue())
  //           .put((signalId, value))
  //     }
  //   }

  // /** Remove all subscriptions for a signal. */
  // def closeSignal(signalId: Identifier): Unit =
  //   signalSubscribers.remove(signalId)

  // /** Drain all pending signal emissions for a peer. */
  // def drainSignalMailbox(peerAddress: String): List[(Identifier, Any)] =
  //   signalMailboxes.get(peerAddress) match
  //     case Some(queue) =>
  //       val buffer = mutable.ListBuffer[(Identifier, Any)]()
  //       var item: (Identifier, Any) | Null = queue.poll()
  //       while item != null do
  //         buffer.addOne(item.nn)
  //         item = queue.poll()
  //       buffer.toList
  //     case None => Nil
end NetworkBroker

object InMemoryNetwork:
  /**
   * Create a network for simulating multiple distributed peers.
   *
   * @param address
   *   the unique address identifying this peer
   * @param broker
   *   the shared (static) broker used as a passive message store
   * @return
   *   a network instance for the given peer
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
end InMemoryNetwork
