package io.github.nicolasfara.stub

import scala.collection.mutable

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.serialization.Decoder

type Id = [A] =>> A

trait IntNetwork extends Network.Effect:
  type Id = Int
  type NetworkError = Throwable
  type Address[P <: Peer] = String

class NoOpIntNetwork extends IntNetwork:
  val setValues = mutable.Set.empty[Any]
  override def localAddress[P <: Peer]: Address[P] = "local"
  override def getId[P <: Peer](address: Address[P]): Id = address.hashCode
  override def register[Container[_], V: Encoder](ref: Reference, data: Container[V]): Unit = ()
  override def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[Address[P]] = Set("remote")
  override def send[Container[_], V: Encoder, To <: Peer, From <: TiedWith[To]](
      address: Address[To],
      ref: Reference,
      data: Container[V],
  ): Either[NetworkError, Unit] =
    setValues.add(data)
    Right(())
  override def receive[Container[_], V: Decoder, From <: Peer, To <: TiedWith[From]](
      address: Address[From],
      ref: Reference,
  ): Either[NetworkError, Container[V]] = Right(42.asInstanceOf[Container[V]])
