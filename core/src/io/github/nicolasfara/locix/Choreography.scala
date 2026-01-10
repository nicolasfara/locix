package io.github.nicolasfara.locix

import scala.annotation.nowarn

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locix.placement.PlacementType.*
import cats.Id

object Choreography:
  opaque type Choreography = Locix[Choreography.Effect]

  def comm[Sender <: TiedToSingle[Receiver]: PeerRepr, Receiver <: TiedToSingle[Sender]: PeerRepr](using
      net: Network,
      placed: PlacedValue,
      choreo: Choreography,
  )[V](value: V on Sender): V on Receiver = choreo.effect.comm(value)

  def run[P <: Peer: PeerRepr](using Network)[V](expression: Choreography ?=> V): V =
    val effect = effectImplementation[P]
    val handler = new Locix.Handler[Choreography.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effect))
    Locix.handle(expression)(using handler)

  private def effectImplementation[LocalPeer <: Peer: PeerRepr] = new Effect:
    override def comm[V, Sender <: TiedToSingle[Receiver]: PeerRepr, Receiver <: TiedToSingle[Sender]: PeerRepr](using
        Network,
        PlacedValue,
    )(value: V on Sender): V on Receiver =
      val senderPeerRepr = summon[PeerRepr[Sender]]
      val receiverPeerRepr = summon[PeerRepr[Receiver]]
      given PeerScope[Sender] = PeerScope[Sender]()
      val (ref, placedValue): (Reference[Sender], Option[Id[V]]) =
        if summon[PeerRepr[LocalPeer]] <:< senderPeerRepr then
          val peer = reachablePeersOf[Receiver]
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Local[V @unchecked, Sender @unchecked](localValue, reference) = value.runtimeChecked
          send[Receiver, Sender, V](peer.head, reference, localValue).fold(throw _, identity)
          (reference, None)
        else
          val peer = reachablePeersOf[Sender]
          require(peer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
          val Placed.Remote[V @unchecked, Sender @unchecked](reference) = value.runtimeChecked
          val receivedValue = receive[Sender, Receiver, Id, V](peer.head, reference).fold(throw _, identity)
          (reference, Some(receivedValue))
      val newRef = Reference(ref.resourceId, receiverPeerRepr, ref.valueType) // TODO: check if it works... probably not
      summon[PlacedValue].effect.liftF(placedValue, newRef)
    end comm

  trait Effect:
    def comm[V, Sender <: TiedToSingle[Receiver]: PeerRepr, Receiver <: TiedToSingle[Sender]: PeerRepr](using
        Network,
        PlacedValue,
    )(value: V on Sender): V on Receiver
end Choreography
