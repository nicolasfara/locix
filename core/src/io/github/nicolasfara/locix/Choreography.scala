package io.github.nicolasfara.locix

import scala.annotation.nowarn

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.serialization.Codec

object Choreography:
  opaque type Choreography = Locix[Choreography.Effect]

  inline def comm[Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
      net: Network,
      placed: PlacedValue,
      choreo: Choreography,
  )[V: Codec](value: V on Sender): V on Receiver = choreo.effect.comm(peer[Sender], peer[Receiver], value)

  @nowarn inline def run[P <: Peer](using Network)[V](expression: Choreography ?=> V): V =
    val localPeerRepr = peer[P]
    val effect = effectImpl[P]
    val handler = new Locix.Handler[Choreography.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effect))
    Locix.handle(expression)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]: Effect = new Effect:
    opaque type Id[V] = V
    override def comm[V: Codec, Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
        Network,
        PlacedValue,
    )(senderPeerRepr: PeerRepr, receiverPeerRepr: PeerRepr, value: V on Sender): V on Receiver =
      given PeerScope[Sender] = PeerScope[Sender]()
      val (ref, placedValue): (Reference, Option[Id[V]]) =
        if peer[LocalPeer] <:< senderPeerRepr then
          val peer = reachablePeers[Receiver](receiverPeerRepr)
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Local[V @unchecked, Sender @unchecked](localValue, reference) = value.runtimeChecked
          send[Receiver, Sender, V](peer.head, reference, localValue).fold(throw _, identity)
          (reference, None)
        else
          val peer = reachablePeers[Sender](senderPeerRepr)
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Remote[V @unchecked, Sender @unchecked](reference) = value.runtimeChecked
          val receivedValue = receive[Sender, Receiver, Id, V](peer.head, reference).fold(throw _, identity)
          (reference, Some(receivedValue))
      summon[PlacedValue].effect.liftF(senderPeerRepr)(placedValue, ref)
    end comm

  trait Effect:
    def comm[V: Codec, Sender <: TiedToSingle[Receiver], Receiver <: TiedToSingle[Sender]](using
        Network,
        PlacedValue,
    )(senderPeerRepr: PeerRepr, receiverPeerRepr: PeerRepr, value: V on Sender): V on Receiver
end Choreography
