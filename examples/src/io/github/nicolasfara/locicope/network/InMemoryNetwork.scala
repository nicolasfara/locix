package io.github.nicolasfara.locicope.network

import scala.concurrent.duration.DurationInt
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import retry.*
import scala.concurrent.Future
import scala.concurrent.Await

enum InMemoryNetworkError extends Throwable:
  case ResourceNotFound(address: String, reference: Reference) extends InMemoryNetworkError

class InMemoryNetwork(val peerRepr: PeerRepr, address: String, id: Int) extends Network.Effect:
  private val localStorage = mutable.Map[Reference, Any]()
  private val receivedStorage = mutable.Map[(String, Reference), Any]()
  private val reachablePeers = mutable.Set[InMemoryNetwork]()

  given eitherSuccess[E]: Success[Either[E, ?]] = Success[Either[E, ?]](_.isRight)

  override type Id = Int
  override type Address[_ <: Peer] = String
  override type NetworkError = InMemoryNetworkError
  override def localAddress[P <: Peer]: Address[P] = address
  override def getId[P <: Peer](address: String): Id = id
  override def register[Container[_], V: Encoder](ref: Reference, data: Container[V]): Unit =
    localStorage(ref) = data
  override def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[String] =
    reachablePeers.filter(_.peerRepr <:< peerRepr).map(_.localAddress).toSet
  override def send[Container[_], V: Encoder, To <: Peer, From <: TiedWith[To]](
      address: String,
      ref: Reference,
      data: Container[V],
  ): Either[NetworkError, Unit] =
    reachablePeers.find(_.localAddress == address) match
      case Some(peer) =>
        peer.deliverMessageFrom[Container, V](this.address, ref, data)
        Right(())
      case None => Left(InMemoryNetworkError.ResourceNotFound(address, ref))

  override def receive[Container[_], V: Decoder, From <: Peer, To <: TiedWith[From]](
      address: String,
      ref: Reference,
  ): Either[NetworkError, Container[V]] =
    val result = retry.Backoff(4, 10.milliseconds).apply { () =>
      Future:
        localStorage
          .get(ref)
          .orElse(receivedStorage.find { case ((fromAddress, r), _) => r.resourceId == ref.resourceId }.map(_._2))
          .toRight(InMemoryNetworkError.ResourceNotFound(address, ref))
          .map(_.asInstanceOf[Container[V]])
    }
    Await.result(result, scala.concurrent.duration.Duration.Inf)

  def deliverMessageFrom[Container[_], V](fromAddress: String, ref: Reference, data: Container[V]): Unit =
    receivedStorage((fromAddress, ref)) = data

  def addReachablePeer(peer: InMemoryNetwork): Unit =
    reachablePeers += peer

  def removeReachablePeer(peer: InMemoryNetwork): Unit =
    reachablePeers -= peer
end InMemoryNetwork
