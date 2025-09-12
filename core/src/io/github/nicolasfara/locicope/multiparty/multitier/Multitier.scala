package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.macros.PlacedFunctionFinder.findPlacedFunctions
import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, TiedToMultiple, TiedToSingle }
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

import scala.util.NotGiven

trait Multitier:
  trait MultitierLabel[+P <: Peer]

  protected val localPeerRepr: PeerRepr

  trait PlacedFunction[-In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable, Local <: Peer]:
    val localPeerRepr: PeerRepr
    val resourceReference: ResourceReference
    override def toString: String = s"λ@${localPeerRepr.baseTypeRepr}"
    def apply(inputs: In): P[Out, Local]

  class PlacedFunctionImpl[Local <: Peer, In <: Product: Codec, Out: Encoder, On[_, _ <: Peer]: Placeable](
      override val localPeerRepr: PeerRepr,
      override val resourceReference: ResourceReference,
  )(
      body: In => On[Out, Local],
  ) extends PlacedFunction[In, Out, On, Local]:
    def apply(inputs: In): On[Out, Local] = body(inputs)

  inline def function[In <: Product: Codec, Out: Codec, On[_, _ <: Peer]: Placeable, Local <: Peer](
      body: MultitierLabel[Local] ?=> In => Out,
  )(using NotGiven[MultitierLabel[Local]], Network): PlacedFunction[In, Out, On, Local] =
    given MultitierLabel[Local]()
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    PlacedFunctionImpl[Local, In, Out, On](peer[Local], resourceReference) { inputs =>
      val result =
        if localPeerRepr <:< peer[Local] then body(inputs)
        else summon[Network].callFunction[In, Out, Local, On](inputs, resourceReference)
      summon[Placeable[On]].lift(Some(result), resourceReference)
    }

  inline def placed[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](body: MultitierLabel[P] ?=> V)(using
      NotGiven[MultitierLabel[P]],
      Network,
  ): F[V, P] =
    scribe.debug("Entering placed function on peer: " + peer[P].baseTypeRepr)
    given MultitierLabel[P]()
    val placedPeerRepr = peer[P]
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    if localPeerRepr <:< placedPeerRepr then
      val bodyValue = body
      summon[Placeable[F]].lift(Some(bodyValue), resourceReference)
    else
      // findPlacedFunctions(body, summon[Network])
      summon[Placeable[F]].lift(None, resourceReference)
  end placed

  inline def placedFlow[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](body: MultitierLabel[P] ?=> Flow[V])(using
      NotGiven[MultitierLabel[P]],
      Network,
  ): F[Flow[V], P] =
    scribe.debug("Entering placed flow on peer: " + peer[P].baseTypeRepr)
    given MultitierLabel[P]()
    val placedPeerRepr = peer[P]
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Flow)
    if localPeerRepr <:< placedPeerRepr then
      val bodyFlow = body
      summon[Placeable[F]].liftFlow(Some(bodyFlow), resourceReference)
    else
      // findPlacedFunctions(body, summon[Network])
      summon[Placeable[F]].liftFlow(None, resourceReference)
  end placedFlow

  extension [V: Decoder, Local <: Peer, F[_, _ <: Peer]: Placeable](placed: F[V, Local])
    def unwrap(using net: Network, ml: MultitierLabel[Local]): V = summon[Placeable[F]].unlift(placed)

  extension [V: Decoder, Local <: Peer, F[_, _ <: Peer]: Placeable](placed: F[Flow[V], Local])
    def unwrap(using net: Network, ml: MultitierLabel[Local]): Flow[V] = summon[Placeable[F]].unliftFlow(placed)

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Placeable](value: F[V, Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using net: Network, mt: Multitier, ml: mt.MultitierLabel[Local]): V =
      summon[Placeable[F]].unlift(value)
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, mt: Multitier, ml: mt.MultitierLabel[Local]): Map[net.ID, V] =
      summon[Placeable[F]].unliftAll(value)

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Placeable](flow: F[Flow[V], Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): Flow[V] =
      summon[Placeable[F]].unliftFlow(flow)
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)] =
      summon[Placeable[F]].unliftFlowAll(flow)
end Multitier

object Multitier:
  inline def function[In <: Product: Codec, Out: Codec, On[_, _ <: Peer]: Placeable, Local <: Peer](using
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[Local]],
      net: Network,
  )(
      body: mt.MultitierLabel[Local] ?=> In => Out,
  ): mt.PlacedFunction[In, Out, On, Local] = mt.function(body)

  inline def placed[P <: Peer](using
      net: Network,
  )(using
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]],
  )[V: Encoder, F[_, _ <: Peer]: Placeable](body: mt.MultitierLabel[P] ?=> V): F[V, P] = mt.placed(body)

  inline def placedFlow[P <: Peer](using
      net: Network,
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]],
  )[V: Encoder, F[_, _ <: Peer]: Placeable](body: mt.MultitierLabel[P] ?=> Flow[V]): F[Flow[V], P] = mt.placedFlow(body)

  inline def run[P <: Peer](using Network)(body: Multitier ?=> Unit): Unit =
    given MultitierImpl(peer[P])
    body
end Multitier
