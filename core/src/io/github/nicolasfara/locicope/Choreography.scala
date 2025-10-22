package io.github.nicolasfara.locicope

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
import scala.annotation.nowarn

object Choreography:
  type Choreography = Locicope[Choreography.Effect]

  inline def comm[V: Codec, Sender <: Peer, Receiver <: TiedToSingle[Sender]](using
      net: Network,
      placed: PlacedValue,
      choreo: Choreography,
      scope: PeerScope[Receiver],
  )(value: V on Sender): V on Receiver = choreo.effect.comm(peer[Sender], value)

  def take[V: Decoder, Local <: Peer](using
      net: Network,
      choreo: Choreography,
      scope: PeerScope[Local],
  )(value: V on Local): V = choreo.effect.take(value)

  @nowarn inline def run[P <: Peer](using Network)[V](expression: Choreography ?=> V): V =
    val localPeerRepr = peer[P]
    val handler = new Locicope.Handler[Choreography.Effect, V, V]:
      override def handle(program: (Locicope[Effect]) ?=> V): V = program(using Locicope(EffectImpl(localPeerRepr)))
    Locicope.handle(expression)(using handler)

  private class EffectImpl(val localPeerRepr: PeerRepr) extends Effect:
    private type Id[V] = V
    override def comm[V: Codec, Remote <: Peer, Local <: TiedToSingle[Remote]](using
        Network,
        PlacedValue,
        PeerScope[Local],
    )(senderPeerRepr: PeerRepr, value: V on Remote): V on Local =
      val peer = reachablePeersOf[Remote]
      assume(peer.size != 1, s"Only 1 peer should be connected to this local peer, but found ${peer}")
      val (ref, placedValue): (Reference, Option[Id[V]]) =
        if senderPeerRepr <:< localPeerRepr then
          val Placed.Local[V @unchecked, Local @unchecked](localValue, reference) = value.runtimeChecked
          send[Id, V, Remote, Local](peer.head, reference, localValue).fold(throw _, identity)
          (reference, None)
        else
          val Placed.Remote[V @unchecked, Local @unchecked](reference) = value.runtimeChecked
          val receivedValue = receive[Id, V, Remote, Local](peer.head, reference).fold(throw _, identity)
          (reference, Some(receivedValue))
      summon[PlacedValue].effect.liftF(senderPeerRepr)(placedValue, ref)

    override def take[V, Local <: Peer](using
        Network,
        PeerScope[Local],
    )(value: V on Local): V =
      val Placed.Local[V @unchecked, Local @unchecked](localValue, _) = value.runtimeChecked
      localValue
  end EffectImpl

  trait Effect:
    protected[locicope] val localPeerRepr: PeerRepr

    def comm[V: Codec, Sender <: Peer, Receiver <: TiedToSingle[Sender]](using
        Network,
        PlacedValue,
        PeerScope[Receiver],
    )(senderPeerRepr: PeerRepr, value: V on Sender): V on Receiver
    def take[V, Local <: Peer](using Network, PeerScope[Local])(value: V on Local): V
end Choreography
