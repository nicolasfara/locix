package io.github.nicolasfara.locicope.network

import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.PlacementType.PeerScope
import io.github.nicolasfara.locicope.placement.Peers.peer
import scala.annotation.targetName
import io.github.nicolasfara.locicope.network.NetworkResource.Reference

object Network:
  type Network = Locicope[Network.Effect]

  def register[V: Encoder](ref: Reference, data: V)(using net: Network): Unit =
    net.effect.register[V](ref, data)

  def send[V: Encoder, To <: Peer, From <: TiedWith[To]](using
      scope: PeerScope[From],
      net: Network,
  )(address: net.effect.Address[To], ref: Reference, data: V): Either[net.effect.NetworkError, Unit] =
    net.effect.send[V, To, From](address, ref, data)

  def receive[V: Decoder, From <: Peer, To <: TiedWith[From]](using
      scope: PeerScope[To],
      net: Network,
  )(address: net.effect.Address[From], ref: Reference): Either[net.effect.NetworkError, V] =
    net.effect.receive[V, From, To](address, ref)

  inline def reachablePeersOf[P <: Peer](using net: Network): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peer[P])

  def reachablePeers[P <: Peer](using net: Network)(peerRepr: PeerRepr): Set[net.effect.Address[P]] =
    net.effect.reachablePeersOf[P](peerRepr)

  trait Effect:
    type Address[_ <: Peer]
    type NetworkError <: Throwable
    type Id

    extension [P <: Peer](address: Address[P]) def id: Id

    def register[V: Encoder](ref: Reference, data: V): Unit

    def send[V: Encoder, To <: Peer, From <: TiedWith[To]](using
        PeerScope[From],
    )(address: Address[To], ref: Reference, data: V): Either[NetworkError, Unit]

    def receive[V: Decoder, From <: Peer, To <: TiedWith[From]](using
        PeerScope[To],
    )(address: Address[From], ref: Reference): Either[NetworkError, V]

    def reachablePeersOf[P <: Peer](peerRepr: PeerRepr): Set[Address[P]]
end Network
