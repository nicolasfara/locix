package io.github.nicolasfara.locix.distributed

import io.github.nicolasfara.locix.network.{Network, NetworkError, Identifier}
import io.github.nicolasfara.locix.peers.Peers.{Peer, TiedWith, PeerTag}
import io.github.nicolasfara.locix.raise.Raise
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{Future, Await, Promise}
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

/** In-memory network implementation simulating distributed peers communication.
  * 
  * This implementation maintains:
  * - Local storage for values and signals
  * - Peer-to-peer communication through shared network broker
  * - Signal subscriptions with reactive primitives support
  * - Retry mechanisms for eventual consistency
  */
private final class InMemoryNetworkImpl[LocalPeer <: Peer: PeerTag](
  private val address: String,
  private val broker: NetworkBroker
) extends Network:
  
  type PeerAddress = String
  type KeyId = String

  // Local storage for values placed on this peer
  private val localStorage = TrieMap[Identifier, Any]()
  
  // Signal subscriptions: signalId -> list of callbacks
  // Using Any to avoid capture checking issues with function types
  private val signalSubscriptions = TrieMap[Identifier, mutable.ListBuffer[Any]]()
  
  // Track which peer types are reachable through the broker
  private val localPeerTag = summon[PeerTag[LocalPeer]]

  override def reachablePeers: Set[PeerAddress] = 
    broker.getAllPeers.filterNot(_ == address)

  override def reachablePeersOf[P <: Peer]: Set[PeerAddress] = 
    // Without runtime type information, return all reachable peers
    // This could be enhanced if PeerTag is made available in the trait
    reachablePeers

  override def peerAddress: PeerAddress = address

  override def push[S <: TiedWith[D], D <: Peer, V](using r: Raise[NetworkError])(
    to: PeerAddress, 
    key: Identifier, 
    value: V
  ): Unit = 
    broker.send(to, key, value) match
      case true => ()
      case false => r.raise(NetworkError.UnreachablePeer(s"Peer $to is not reachable"))

  override def pull[From <: TiedWith[To], To <: Peer, V](using r: Raise[NetworkError])(
    from: PeerAddress, 
    key: Identifier
  ): V = 
    // Create a correlation ID for this request-response
    val correlationId = s"${address}-${key.id}-${System.nanoTime()}"
    val responsePromise = Promise[Any]()
    
    broker.registerPullRequest(correlationId, responsePromise)
    
    // Send request to remote peer
    if !broker.sendPullRequest(from, key, correlationId, address) then
      r.raise(NetworkError.UnreachablePeer(s"Cannot reach peer $from"))
    
    // Wait for response with timeout
    Try(Await.result(responsePromise.future, 5.seconds)) match
      case Success(value) => value.asInstanceOf[V]
      case Failure(ex) => r.raise(NetworkError.NetworkFailure(s"Pull failed: ${ex.getMessage}"))

  override def broadcast[S <: Peer, V](using r: Raise[NetworkError])(
    key: Identifier, 
    value: V
  ): Unit = 
    broker.broadcast(address, key, value)

  override def retrieve[S <: Peer, V](using r: Raise[NetworkError])(
    key: Identifier
  ): V = 
    // First check local storage
    localStorage.get(key) match
      case Some(value) => value.asInstanceOf[V]
      case None =>
        // Retry mechanism with exponential backoff
        val maxRetries = 10
        val baseDelay = 50.millis
        
        def tryRetrieve(attempt: Int): V =
          localStorage.get(key) match
            case Some(value) => value.asInstanceOf[V]
            case None if attempt >= maxRetries =>
              r.raise(NetworkError.KeyNotFound(key))
            case None =>
              Thread.sleep(baseDelay.toMillis * Math.pow(1.5, attempt).toLong)
              tryRetrieve(attempt + 1)
        
        tryRetrieve(0)

  override def store[V](key: Identifier, value: V): Unit = 
    localStorage.put(key, value)

  override def emit[V](key: Identifier, value: V): Unit = 
    // Emit to local subscribers
    signalSubscriptions.get(key).foreach { callbacks =>
      callbacks.foreach { cb => 
        Try(cb.asInstanceOf[V => Unit](value))
      }
    }
    // Broadcast emission to other peers for their subscribers
    broker.emitSignal(address, key, value)

  override def close(key: Identifier): Unit = 
    // Notify local subscribers
    signalSubscriptions.remove(key)
    // Broadcast closure to other peers
    broker.closeSignal(address, key)

  override def subscribe[V](to: PeerAddress, signalId: Identifier, callback: V => Unit): Unit = 
    val callbacks = signalSubscriptions.getOrElseUpdate(
      signalId, 
      mutable.ListBuffer[Any]()
    )
    callbacks.addOne(callback.asInstanceOf[Any])
    
    // Register subscription with broker for remote signals
    if to != address then
      broker.registerSubscription(to, signalId, address)

  override def unsubscribe(to: PeerAddress, signalId: Identifier): Unit = 
    signalSubscriptions.remove(signalId)
    if to != address then
      broker.unregisterSubscription(to, signalId, address)

  // Internal method called by broker to deliver values
  private[distributed] def deliverValue(key: Identifier, value: Any): Unit =
    localStorage.put(key, value)

  // Internal method called by broker to handle pull requests
  private[distributed] def handlePullRequest(key: Identifier, correlationId: String, requester: PeerAddress): Unit =
    localStorage.get(key) match
      case Some(value) => 
        broker.sendPullResponse(requester, correlationId, value)
      case None => 
        broker.sendPullResponse(requester, correlationId, NetworkError.KeyNotFound(key))

  // Internal method called by broker to deliver signal emissions
  private[distributed] def deliverSignalEmit(signalId: Identifier, value: Any): Unit =
    signalSubscriptions.get(signalId).foreach { callbacks =>
      callbacks.foreach { cb => 
        Try(cb.asInstanceOf[Any => Unit](value))
      }
    }

  // Internal method called by broker to handle signal closures
  private[distributed] def handleSignalClose(signalId: Identifier): Unit =
    signalSubscriptions.remove(signalId)

/** Centralized broker managing all peer-to-peer communications.
  * 
  * This simulates the network layer, routing messages between peers
  * while maintaining awareness of peer types and connectivity.
  */
private class NetworkBroker:
  // Map peer addresses to their network implementations
  // Store as Any to avoid capture checking issues, cast when retrieving
  private val peers = TrieMap[String, Any]()
  
  // Map peer addresses to their peer type names
  private val peerTypes = TrieMap[String, String]()
  
  // Map correlation IDs to response promises for pull operations
  private val pullRequests = TrieMap[String, Promise[Any]]()
  
  // Map signal IDs to subscribing peer addresses
  private val signalSubscribers = TrieMap[Identifier, mutable.Set[String]]()

  def registerPeer[P <: Peer: PeerTag](address: String, network: InMemoryNetworkImpl[P]): Unit =
    peers.put(address, network)
    peerTypes.put(address, summon[PeerTag[P]].baseTypeRepr)

  def unregisterPeer(address: String): Unit =
    peers.remove(address)
    peerTypes.remove(address)

  def getAllPeers: Set[String] = peers.keySet.toSet

  def getPeersOfType(peerType: String): Set[String] =
    peerTypes.filter(_._2 == peerType).keySet.toSet

  def send(to: String, key: Identifier, value: Any): Boolean =
    peers.get(to) match
      case Some(peer: InMemoryNetworkImpl[?]) =>
        peer.deliverValue(key, value)
        true
      case _ => false

  def broadcast(from: String, key: Identifier, value: Any): Unit =
    val peerList = peers.toList
    peerList.foreach { case (address, peer: Any) =>
      if address != from then
        peer.asInstanceOf[InMemoryNetworkImpl[?]].deliverValue(key, value)
    }

  def sendPullRequest(to: String, key: Identifier, correlationId: String, requester: String): Boolean =
    peers.get(to) match
      case Some(peer: InMemoryNetworkImpl[?]) =>
        peer.handlePullRequest(key, correlationId, requester)
        true
      case _ => false

  def registerPullRequest(correlationId: String, promise: Promise[Any]): Unit =
    pullRequests.put(correlationId, promise)

  def sendPullResponse(to: String, correlationId: String, value: Any): Unit =
    pullRequests.remove(correlationId).foreach { promise =>
      value match
        case err: NetworkError => promise.failure(new RuntimeException(err.toString))
        case v => promise.success(v)
    }

  def emitSignal(from: String, signalId: Identifier, value: Any): Unit =
    signalSubscribers.get(signalId).foreach { subscribers =>
      val subscriberList = subscribers.toList
      subscriberList.foreach { subscriber =>
        peers.get(subscriber).foreach: peer =>
          peer.asInstanceOf[InMemoryNetworkImpl[?]].deliverSignalEmit(signalId, value)
      }
    }

  def closeSignal(from: String, signalId: Identifier): Unit =
    signalSubscribers.get(signalId).foreach { subscribers =>
      val subscriberList = subscribers.toList
      subscriberList.foreach { subscriber =>
        peers.get(subscriber).foreach: peer =>
          peer.asInstanceOf[InMemoryNetworkImpl[?]].handleSignalClose(signalId)
      }
    }
    signalSubscribers.remove(signalId)

  def registerSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
    signalSubscribers.getOrElseUpdate(signalId, mutable.Set.empty).add(subscriber)

  def unregisterSubscription(signalSource: String, signalId: Identifier, subscriber: String): Unit =
    signalSubscribers.get(signalId).foreach(_.remove(subscriber))

object InMemoryNetwork:
  /** Create a network for simulating multiple distributed peers.
    * 
    * @param address the unique address identifying this peer
    * @param broker the shared broker managing communication between all peers
    * @return a network instance for the given peer
    */
  def apply[P <: Peer: PeerTag](address: String, broker: NetworkBroker): Network =
    val network = InMemoryNetworkImpl[P](address, broker)
    broker.registerPeer(address, network)
    network
  
  /** Create a network broker for coordinating multiple peers. */
  def broker(): NetworkBroker = NetworkBroker()
  
  /** Convenience method to create a network with a new broker. */
  def standalone[P <: Peer: PeerTag](address: String): (Network, NetworkBroker) =
    val b = broker()
    val net = apply[P](address, b)
    (net, b)
