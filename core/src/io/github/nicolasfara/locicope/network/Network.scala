package io.github.nicolasfara.locicope.network

import scala.annotation.targetName

import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import cats.data.Cont
import io.github.nicolasfara.locicope.serialization.Codec

object Network:
  type Network = Locicope[Network.Effect]

  case class FlowTermination()

  private[locicope] def terminateFlow[P <: Peer](using
      net: Network,
  )(address: net.effect.Address[P], ref: Reference): Either[net.effect.NetworkError, Unit] =
    net.effect.terminateFlow[P](address, ref)

  def localAddress[P <: Peer](using net: Network): net.effect.Address[P] =
    net.effect.localAddress[P]

  def getId[P <: Peer](using net: Network)(address: net.effect.Address[P]): net.effect.Id =
    net.effect.getId[P](address)

  def register[F[_], V: Encoder](ref: Reference, data: F[V])(using net: Network): Unit =
    net.effect.register[F, V](ref, data)

  def send[To <: Peer, From <: TiedWith[To], V: Encoder](using
      net: Network,
  )(address: net.effect.Address[To], ref: Reference, data: V): Either[net.effect.NetworkError, Unit] =
    net.effect.send[To, From, V](address, ref, data)

  def receive[From <: Peer, To <: TiedWith[From], F[_], V: Decoder](using
      // scope: PeerScope[To],
      net: Network,
  )(address: net.effect.Address[From], ref: Reference): Either[net.effect.NetworkError, F[V]] =
    net.effect.receive[From, To, F, V](address, ref)

  inline def reachablePeersOf[P <: Peer](using net: Network): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peer[P])

  def reachablePeers[P <: Peer](using net: Network)(peerRepr: PeerRepr): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peerRepr)

  extension [P <: Peer](using net: Network)(address: net.effect.Address[P]) def id: net.effect.Id = net.effect.getId[P](address)

  trait Effect:
    type Address[_ <: Peer]
    type NetworkError <: Throwable
    type Id

    given flowTerminatorCodec: Codec[FlowTermination] = scala.compiletime.deferred

    private[locicope] def terminateFlow[P <: Peer](address: Address[P], ref: Reference): Either[NetworkError, Unit] =
      send(address, ref, FlowTermination())

    def localAddress[P <: Peer]: Address[P]

    def getId[P <: Peer](address: Address[P]): Id

    def register[F[_], V: Encoder](ref: Reference, data: F[V]): Unit

    def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[Address[P]]

    def send[To <: Peer, From <: TiedWith[To], V: Encoder](
        address: Address[To],
        ref: Reference,
        data: V,
    ): Either[NetworkError, Unit]

    def receive[From <: Peer, To <: TiedWith[From], F[_], V: Decoder](
        address: Address[From],
        ref: Reference,
    ): Either[NetworkError, F[V]]
  end Effect
end Network
