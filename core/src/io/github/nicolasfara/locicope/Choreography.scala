package io.github.nicolasfara.locicope

import scala.annotation.nowarn

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.TiedToSingle
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.placement.PlacementType.getReference
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.network.Network.receive
import io.github.nicolasfara.locicope.placement.PlacementType.Placed
import io.github.nicolasfara.locicope.network.Network.reachablePeersOf
import io.github.nicolasfara.locicope.network.Network.send
import io.github.nicolasfara.locicope.placement.PlacementType
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.network.Network.reachablePeers

object Choreography:
  opaque type Choreography = Locicope[Choreography.Effect]

  inline def comm[Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
      net: Network,
      placed: PlacedValue,
      choreo: Choreography,
  )[V: Codec](value: V on Sender): V on Receiver = choreo.effect.comm(peer[Sender], peer[Receiver], value)

  @nowarn inline def run[P <: Peer](using Network)[V](expression: Choreography ?=> V): V =
    val localPeerRepr = peer[P]
    val effect = effectImpl[P]
    val handler = new Locicope.Handler[Choreography.Effect, V, V]:
      override def handle(program: (Locicope[Effect]) ?=> V): V = program(using Locicope(effect))
    Locicope.handle(expression)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]: Effect = new Effect:
    opaque type Id[V] = V
    override def comm[V: Codec, Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
        Network,
        PlacedValue,
    )(senderPeerRepr: PeerRepr, receiverPeerRepr: PeerRepr, value: V on Sender): V on Receiver =
      import scala.compiletime.summonFrom
      given PeerScope[Sender] {}
      val (ref, placedValue): (Reference, Option[Id[V]]) =
        if peer[LocalPeer] <:< senderPeerRepr then
          val peer = reachablePeers[Receiver](receiverPeerRepr)
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Local[V @unchecked, Sender @unchecked](localValue, reference) = value.runtimeChecked
          send[Id, V, Receiver, Sender](peer.head, reference, localValue).fold(throw _, identity)
          (reference, None)
        else
          val peer = reachablePeers[Sender](senderPeerRepr)
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Remote[V @unchecked, Sender @unchecked](reference) = value.runtimeChecked
          val receivedValue = receive[Id, V, Sender, Receiver](peer.head, reference).fold(throw _, identity)
          (reference, Some(receivedValue))
      summon[PlacedValue].effect.liftF(senderPeerRepr)(placedValue, ref)
    end comm

  trait Effect:
    def comm[V: Codec, Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
        Network,
        PlacedValue,
    )(senderPeerRepr: PeerRepr, receiverPeerRepr: PeerRepr, value: V on Sender): V on Receiver
end Choreography
