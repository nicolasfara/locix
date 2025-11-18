package io.github.nicolasfara.locix.network

import scala.collection.{concurrent, mutable}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import io.github.nicolasfara.locix.CirceCodec.given
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Network.FlowTermination
import io.github.nicolasfara.locix.network.NetworkResource.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.serialization.*
import ox.channels.Channel
import ox.flow.Flow
import retry.*

enum InMemoryNetworkError extends Throwable:
  case ResourceNotFound(address: String, reference: Reference) extends InMemoryNetworkError
  case PeerNotReachable(address: String) extends InMemoryNetworkError

class InMemoryNetwork(val peerRepr: PeerRepr, address: String, id: Int) extends Network.Effect:
  private val localStorage = mutable.Map[Reference, Any]()
  private val receivedStorage = concurrent.TrieMap[(String, Reference), Any]()
  private val flowReceivedStorage = concurrent.TrieMap[(String, Reference), Channel[Any]]()
  private val reachablePeers = mutable.Set[InMemoryNetwork]()

  given eitherSuccess[E]: Success[Either[E, ?]] = Success[Either[E, ?]](_.isRight)

  override given flowTerminatorCodec: Codec[FlowTermination] = summon

  override type Id = Int
  override type Address[_ <: Peer] = String
  override type NetworkError = InMemoryNetworkError
  override def localAddress[P <: Peer]: Address[P] = address
  override def getId[P <: Peer](address: String): Id =
    if address == localAddress then id
    else
      val peer = reachablePeers.find(_.localAddress == address).get
      peer.getId(peer.localAddress)
  override def register[Container[_], V: Encoder](ref: Reference, data: Container[V]): Unit =
    localStorage(ref) = data
  override def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[String] =
    reachablePeers /*.filter(_.peerRepr <:< peerRepr)*/ .map(_.localAddress).toSet
  override def send[To <: Peer, From <: TiedWith[To], V: Encoder](
      address: String,
      ref: Reference,
      data: V,
  ): Either[NetworkError, Unit] =
    reachablePeers.find(_.localAddress == address) match
      case Some(peer) =>
        peer.deliverMessageFrom[V](this.address, ref, data)
        Right(())
      case None => Left(InMemoryNetworkError.PeerNotReachable(address))

  override def receive[From <: Peer, To <: TiedWith[From], F[_], V: Decoder](
      address: String,
      ref: Reference,
  ): Either[NetworkError, F[V]] =
    val result = retry.Backoff(4, 100.milliseconds).apply { () =>
      Future:
        localStorage
          .get(ref)
          .orElse(receivedStorage.get((address, ref)))
          .orElse(flowReceivedStorage.get((address, ref)))
          .toRight(InMemoryNetworkError.ResourceNotFound(address, ref))
          .map:
            case data: Channel[?] => Flow.fromSource(data).asInstanceOf[F[V]]
            case elem => elem.asInstanceOf[F[V]]
    }
    Await.result(result, scala.concurrent.duration.Duration.Inf)

  def deliverMessageFrom[V](fromAddress: String, ref: Reference, data: V): Unit =
    ref.valueType match
      case ValueType.Flow =>
        val channel = flowReceivedStorage
          .getOrElseUpdate((fromAddress, ref), Channel.unlimited[Any])
        data match
          case _: FlowTermination => channel.done()
          case _ => channel.send(data)
      case ValueType.Value =>
        receivedStorage((fromAddress, ref)) = data

  def addReachablePeer(peer: InMemoryNetwork): Unit =
    reachablePeers += peer

  def removeReachablePeer(peer: InMemoryNetwork): Unit =
    reachablePeers -= peer
end InMemoryNetwork
