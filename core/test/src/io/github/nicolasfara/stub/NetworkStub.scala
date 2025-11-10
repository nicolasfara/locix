package io.github.nicolasfara.stub

import scala.collection.mutable

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.network.Network.FlowTermination
import io.github.nicolasfara.locicope.utils.TestCodec.given

type Id = [A] =>> A

trait IntNetwork extends Network.Effect:
  type Id = Int
  type NetworkError = Throwable
  type Address[P <: Peer] = String

  override given flowTerminatorCodec: Codec[FlowTermination] = summon

class NoOpIntNetwork extends IntNetwork:
  val setValues = mutable.Set.empty[Any]
  override def localAddress[P <: Peer]: Address[P] = "local"
  override def getId[P <: Peer](address: Address[P]): Id = address.hashCode
  override def register[Container[_], V: Encoder](ref: Reference, data: Container[V]): Unit = ()
  override def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[Address[P]] = Set("remote")
  override def send[To <: Peer, From <: TiedWith[To], V: Encoder](
      address: Address[To],
      ref: Reference,
      data: V,
  ): Either[NetworkError, Unit] =
    setValues.add(data)
    Right(())
  override def receive[From <: Peer, To <: TiedWith[From], F[_], V: Decoder](
      address: Address[From],
      ref: Reference,
  ): Either[NetworkError, F[V]] = Right(42.asInstanceOf[F[V]])
