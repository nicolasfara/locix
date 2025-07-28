package io.github.nicolasfara.locicope.multiparty.multitier

import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.macros.PlacedFunctionFinder.findPlacedFunctions
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.{ Multiple, Single }
import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, TiedToMultiple, TiedToSingle }
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

import scala.annotation.targetName
import scala.util.NotGiven

trait Multitier:
  trait MultitierLabel[+P <: Peer]

  protected val localPeerRepr: PeerRepr

  trait PlacedFunction[Local <: Peer, -In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable]:
    val localPeerRepr: PeerRepr
    val resourceReference: ResourceReference
    override def toString: String = s"λ@${localPeerRepr.baseTypeRepr}"
    def apply(inputs: In): P[Out, Local]

  class PlacedFunctionImpl[Local <: Peer, In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable](
      override val localPeerRepr: PeerRepr,
      override val resourceReference: ResourceReference,
  )(
      body: In => P[Out, Local],
  ) extends PlacedFunction[Local, In, Out, P]:
    def apply(inputs: In): P[Out, Local] = body(inputs)

  inline def function[In <: Product: Codec, Out: Codec, P[_, _ <: Peer]: Placeable, Local <: Peer](
      body: MultitierLabel[Local] ?=> In => Out,
  )(using NotGiven[MultitierLabel[Local]], Network): PlacedFunction[Local, In, Out, P] =
    given MultitierLabel[Local]()
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    PlacedFunctionImpl[Local, In, Out, P](peer[Local], resourceReference) { inputs =>
      val result =
        if localPeerRepr <:< peer[Local] then body(inputs)
        else summon[Network].callFunction[In, Out, Local, P](inputs, resourceReference)
      summon[Placeable[P]].lift(Some(result), resourceReference)
    }

  inline def placed[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](body: MultitierLabel[P] ?=> V)(using
      NotGiven[MultitierLabel[P]],
      Network,
  ): F[V, P] =
    scribe.info("Entering placed function on peer: " + peer[P].baseTypeRepr)
    given MultitierLabel[P]()
    val placedPeerRepr = peer[P]
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    if localPeerRepr <:< placedPeerRepr then
      val bodyValue = body
      summon[Placeable[F]].lift(Some(bodyValue), resourceReference)
    else
      findPlacedFunctions(body, summon[Network])
      summon[Placeable[F]].lift(None, resourceReference)
  end placed

  inline def placedFlow[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](body: MultitierLabel[P] ?=> Flow[V])(using
      NotGiven[MultitierLabel[P]],
      Network,
  ): F[Flow[V], P] = ???

  extension [V: Decoder, Local <: Peer, F[_, _ <: Peer]: Placeable](placed: F[V, Local])
    def unwrap(using net: Network, ml: MultitierLabel[Local]): V = summon[Placeable[F]].unlift(placed)

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Placeable](value: F[V, Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): V =
      summon[Placeable[F]].unlift(value)
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V] =
      ???

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Placeable](flow: F[Flow[V], Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): Flow[V] = ???
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)] =
      ???
end Multitier

object Multitier:
  inline def function[In <: Product: Codec, Out: Codec, P[_, _ <: Peer]: Placeable, Local <: Peer](using
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[Local]],
      net: Network,
  )(
      body: mt.MultitierLabel[Local] ?=> In => Out,
  ): mt.PlacedFunction[Local, In, Out, P] = mt.function(body)

  inline def placed[P <: Peer](using
      net: Network,
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]],
  )[V: Encoder, F[_, _ <: Peer]: Placeable](body: mt.MultitierLabel[P] ?=> V): F[V, P] = mt.placed(body)

  inline def placedFlow[P <: Peer](using
      net: Network,
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]],
  )[V: Encoder, F[_, _ <: Peer]: Placeable](body: mt.MultitierLabel[P] ?=> Flow[V]): F[Flow[V], P] = mt.placedFlow(body)

  inline def multitier[P <: Peer](body: Multitier ?=> Unit)(using Network): Unit =
    given MultitierImpl(peer[P])
    body
end Multitier
