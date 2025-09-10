package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.PlacementType.on
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.PlacementType.PeerScope
import ox.flow.Flow
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import scala.util.NotGiven
import io.github.nicolasfara.locicope.placement.Peers.peer

object Multitier:
  type Multitier = Locicope[Multitier.Effect]
  opaque type MultitierPeerScope[P <: Peer] = MultitierPeerScopeImpl[P]

  inline def placed[P <: Peer](using Net, Multitier, NotGiven[MultitierPeerScope[P]])[V: Encoder](body: PeerScope[P] ?=> V): V on P =
    summon[Multitier].effect.placed[V, P](body)(peer[P])

  inline def placedFlow[P <: Peer](using Net, Multitier, NotGiven[MultitierPeerScope[P]])[V: Encoder](body: PeerScope[P] ?=> Flow[V]): Flow[V] on P =
    summon[Multitier].effect.placedFlow[V, P](body)(peer[P])

  inline def function[P <: Peer](using
      net: Net,
      mt: Multitier,
      ng: NotGiven[MultitierPeerScope[P]],
  )[In <: Product: Codec, Out: Codec](body: PeerScope[P] ?=> (In => Out)): mt.effect.PlacedFunction[In, Out, P] =
    summon[Multitier].effect.function[In, Out, P](body)(peer[P])

  inline def run[P <: Peer](using Net)[V](program: Multitier ?=> V): Unit =
    val handler = MultitierHandlerImpl[V](peer[P])
    Locicope.handle(program)(using handler)

  private class MultitierPeerScopeImpl[P <: Peer](val peerRepr: PeerRepr) extends PlacementType.PeerScope[P]

  class MultitierHandlerImpl[V](peerRepr: PeerRepr) extends Locicope.Handler[Multitier.Effect, V, Unit]:
    override def handle(program: Locicope[Effect] ?=> V): Unit = program(using new Locicope(EffectImpl(peerRepr)))

  private class EffectImpl(val localPeerRepr: PeerRepr) extends Effect:
    private class PlacedFunctionImpl[In <: Product: Codec, Out: Encoder, Local <: Peer](
        val localPeerRepr: PeerRepr,
        val resourceReference: ResourceReference,
    ) extends PlacedFunction[In, Out, Local]:
      override def toString: String = s"λ@${localPeerRepr.baseTypeRepr}"
      override def apply(inputs: In): Out on Local = ???

    override def placed[V: Encoder, P <: Peer](body: (PeerScope[P]) ?=> V)(peerRepr: PeerRepr)(using Net): V on P = ???

    override def placedFlow[V: Encoder, P <: Peer](body: (PeerScope[P]) ?=> Flow[V])(peerRepr: PeerRepr)(using Net): Flow[V] on P = ???

    override def function[In <: Product: Codec, Out: Codec, P <: Peer](body: (PeerScope[P]) ?=> In => Out)(peerRepr: PeerRepr)(using
        Net,
    ): PlacedFunction[In, Out, P] = ???

  trait Effect:
    trait PlacedFunction[-In <: Product: Codec, Out: Encoder, Local <: Peer]:
      val localPeerRepr: PeerRepr
      val resourceReference: ResourceReference
      def apply(inputs: In): Out on Local

    def placed[V: Encoder, P <: Peer](body: PeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net): V on P
    def placedFlow[V: Encoder, P <: Peer](body: PeerScope[P] ?=> Flow[V])(peerRepr: PeerRepr)(using Net): Flow[V] on P
    def function[In <: Product: Codec, Out: Codec, P <: Peer](body: PeerScope[P] ?=> (In => Out))(peerRepr: PeerRepr)(using
        Net,
    ): PlacedFunction[In, Out, P]
end Multitier
