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

object Network:
  type Network = Locicope[Network.Effect]

  def localAddress[P <: Peer](using net: Network): net.effect.Address[P] =
    net.effect.localAddress[P]

  def getId[P <: Peer](using net: Network)(address: net.effect.Address[P]): net.effect.Id =
    net.effect.getId[P](address)

  def register[Container[_], V: Encoder](ref: Reference, data: Container[V])(using net: Network): Unit =
    net.effect.register[Container, V](ref, data)

  def send[Container[_], V: Encoder, To <: Peer, From <: TiedWith[To]](using
      net: Network,
  )(address: net.effect.Address[To], ref: Reference, data: Container[V]): Either[net.effect.NetworkError, Unit] =
    net.effect.send[Container, V, To, From](address, ref, data)

  def receive[Container[_], V: Decoder, From <: Peer, To <: TiedWith[From]](using
      scope: PeerScope[To],
      net: Network,
  )(address: net.effect.Address[From], ref: Reference): Either[net.effect.NetworkError, Container[V]] =
    net.effect.receive[Container, V, From, To](address, ref)

  inline def reachablePeersOf[P <: Peer](using net: Network): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peer[P])

  def reachablePeers[P <: Peer](using net: Network)(peerRepr: PeerRepr): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peerRepr)

  extension [P <: Peer](using net: Network)(address: net.effect.Address[P]) def id: net.effect.Id = net.effect.getId[P](address)

  trait Effect:
    type Address[_ <: Peer]
    type NetworkError <: Throwable
    type Id

    def localAddress[P <: Peer]: Address[P]

    def getId[P <: Peer](address: Address[P]): Id

    def register[Container[_], V: Encoder](ref: Reference, data: Container[V]): Unit

    def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[Address[P]]

    def send[Container[_], V: Encoder, To <: Peer, From <: TiedWith[To]](
        address: Address[To],
        ref: Reference,
        data: Container[V],
    ): Either[NetworkError, Unit]

    def receive[Container[_], V: Decoder, From <: Peer, To <: TiedWith[From]](
        address: Address[From],
        ref: Reference,
    ): Either[NetworkError, Container[V]]
  end Effect
end Network
