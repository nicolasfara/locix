package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.PlacementType.on
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.PlacementType.PeerScope
import ox.flow.Flow
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import scala.util.NotGiven
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import io.github.nicolasfara.locicope.Net.registerFunction
import io.github.nicolasfara.locicope.Net.invokeFunction
import io.github.nicolasfara.locicope.macros.PlacedFunctionFinder.findPlacedFunctions

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

  private class EffectImpl(override val localPeerRepr: PeerRepr) extends Effect:
    private class PlacedFunctionImpl[In <: Product: Codec, Out: Codec, Local <: Peer](
        override val funcPeerRepr: PeerRepr,
        override val resourceReference: Reference,
    )(override val body: In => Out)(using Net) extends PlacedFunction[In, Out, Local]:
      override def toString: String = s"λ@${funcPeerRepr.baseTypeRepr}"
      override def apply(inputs: In): Out on Local =
        val result = if localPeerRepr <:< funcPeerRepr then body(inputs)
        else invokeFunction[In, Out](inputs, resourceReference)
        PlacementType.lift(Some(result), resourceReference)

    override def placed[V: Encoder, P <: Peer](body: MultitierPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net): V on P =
      given MultitierPeerScope[P] = new MultitierPeerScopeImpl[P](peerRepr)
      val resourceReference = Reference(hashBody(body), localPeerRepr, ValueType.Value)
      val placedValue = if localPeerRepr <:< peerRepr then
        val result = body
        Some(result) 
      else
        findPlacedFunctions(body, summon)
        None
      PlacementType.lift(placedValue, resourceReference)

    override def placedFlow[V: Encoder, P <: Peer](body: MultitierPeerScope[P] ?=> Flow[V])(peerRepr: PeerRepr)(using Net): Flow[V] on P =
      given MultitierPeerScope[P] = new MultitierPeerScopeImpl[P](peerRepr)
      val resourceReference = Reference(hashBody(body), localPeerRepr, ValueType.Flow)
      val placedValue = if localPeerRepr <:< peerRepr then
        val result = body
        Some(result) 
      else
        findPlacedFunctions(body, summon)
        None
      PlacementType.liftFlow(placedValue, resourceReference)

    override def function[In <: Product: Codec, Out: Codec, P <: Peer](body: MultitierPeerScope[P] ?=> In => Out)(peerRepr: PeerRepr)(using
        Net,
    ): PlacedFunction[In, Out, P] = 
      given MultitierPeerScope[P] = new MultitierPeerScopeImpl[P](peerRepr)
      val resourceReference = Reference(hashBody(body), localPeerRepr, ValueType.Value)
      PlacedFunctionImpl[In, Out, P](peerRepr, resourceReference)(body)

  trait Effect:
    protected[locicope] val localPeerRepr: PeerRepr

    trait PlacedFunction[-In <: Product: Codec, Out: Encoder, Local <: Peer]:
      val funcPeerRepr: PeerRepr
      val resourceReference: Reference
      val body: In => Out
      def apply(inputs: In): Out on Local

    def placed[V: Encoder, P <: Peer](body: MultitierPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net): V on P
    def placedFlow[V: Encoder, P <: Peer](body: MultitierPeerScope[P] ?=> Flow[V])(peerRepr: PeerRepr)(using Net): Flow[V] on P
    def function[In <: Product: Codec, Out: Codec, P <: Peer](body: MultitierPeerScope[P] ?=> (In => Out))(peerRepr: PeerRepr)(using
        Net,
    ): PlacedFunction[In, Out, P]
end Multitier
