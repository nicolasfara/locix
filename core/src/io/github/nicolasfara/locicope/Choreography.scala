package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.PlacementType.{ on, unwrap, PeerScope }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType.Value
import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, TiedWith }
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }

import scala.util.NotGiven

object Choreography:
  type Choreography = Locicope[Choreography.Effect]

  inline def at[P <: Peer](using Net, Choreography, NotGiven[ChoreoPeerScope[P]])[V: Encoder](body: PeerScope[P] ?=> V): V on P =
    summon[Choreography].effect.at[V, P](body)(peer[P])

  inline def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](using
      Net,
      Choreography,
      ChoreoPeerScope[Sender],
  )(value: V on Sender): V on Receiver =
    summon[Choreography].effect.comm[V, Sender, Receiver](value)

  inline def run[P <: Peer](using Net)[V](program: Choreography ?=> V): Unit =
    val handler: ChoreoHandler[V] = ChoreoHandler[V](peer[P])
    Locicope.handle(program)(using handler)

  class ChoreoPeerScope[P <: Peer](val peerRepr: PeerRepr) extends PeerScope[P]

  class ChoreoHandler[V](peerRepr: PeerRepr) extends Locicope.Handler[Choreography.Effect, V, Unit]:
    override def handle(program: Locicope[Effect] ?=> V): Unit = program(using new Locicope(EffectImpl(peerRepr)))

  class EffectImpl(val localPeerRepr: PeerRepr) extends Effect:
    override def at[V: Encoder, P <: Peer](body: ChoreoPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net, NotGiven[ChoreoPeerScope[P]]): V on P =
      given ChoreoPeerScope[P](peerRepr)
      val resourceReference = ResourceReference(hashBody(body), localPeerRepr, Value)
      val placementValue = if peerRepr <:< localPeerRepr then
        val result = body
        Some(result)
      else None
      PlacementType.lift(placementValue, resourceReference)

    override def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](value: V on Sender)(using Net, ChoreoPeerScope[Sender]): V on Receiver =
      val senderPeerRepr = summon[ChoreoPeerScope[Sender]].peerRepr
      val ref = PlacementType.getRef(value)
      if senderPeerRepr <:< localPeerRepr then
        val localValue = value.unwrap
        summon[Net].effect.setValue(localValue, ref)
        PlacementType.lift(None, ref)
      else
        summon[Net].effect.getValue(ref) match
          case Left(ex) => throw IllegalStateException("Value not found", ex)
          case Right(v) => PlacementType.lift(Some(v), ref)

  trait Effect:
    protected[locicope] val localPeerRepr: PeerRepr

    def at[V: Encoder, P <: Peer](body: ChoreoPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net, NotGiven[ChoreoPeerScope[P]]): V on P
    def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](value: V on Sender)(using Net, ChoreoPeerScope[Sender]): V on Receiver
end Choreography
