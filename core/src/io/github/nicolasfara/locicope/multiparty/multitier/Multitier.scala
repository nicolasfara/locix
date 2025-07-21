package io.github.nicolasfara.locicope.multiparty.multitier

import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.{ Multiple, Single }
import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, TiedToMultiple, TiedToSingle }
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.{ PlaceableFlow, PlaceableValue }
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

import scala.util.NotGiven

trait Multitier:
  trait MultitierLabel[+P <: Peer]

  protected val localPeerRepr: PeerRepr

  trait PlacedFunction[Local <: Peer, In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: PlaceableValue]:
    val localPeerRepr: PeerRepr
    def apply(inputs: In): P[Out, Local]

  def function[In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: PlaceableValue, Local <: Peer](
      body: MultitierLabel[Local] ?=> In => Out,
  )(using NotGiven[MultitierLabel[Local]]): PlacedFunction[Local, In, Out, P]

  inline def placed[V: Encoder, P <: Peer, F[_, _ <: Peer]: PlaceableValue](body: MultitierLabel[P] ?=> V)(using
      NotGiven[MultitierLabel[P]],
      Network,
  ): F[V, P] =
    scribe.info("Entering placed function on peer: " + peer[P].baseTypeRepr)
    given MultitierLabel[P]()
    val placedPeerRepr = peer[P]
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    if localPeerRepr <:< placedPeerRepr then
      val bodyValue = body
      summon[PlaceableValue[F]].lift(Some(bodyValue), resourceReference)
    else
      // TODO: uncomment
//      deps
//        .filter(localPeerRepr <:< _.localPeerRepr)
//        .tapEach(f => scribe.info(s"Registering dependency: $f"))
//        .foreach(summon[Network].registerFunction(_))
      summon[PlaceableValue[F]].lift(None, resourceReference)

  protected def _asLocal[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: PlaceableValue](
      effect: F[V, Remote],
  )(using Network, MultitierLabel[Local]): V

  protected def _asLocalAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: PlaceableValue](
      effect: F[V, Remote],
  )(using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V]

  protected def _asLocalFlow[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: PlaceableFlow](
      flow: F[Flow[V], Remote],
  )(using Network, MultitierLabel[Local]): Flow[V]

  protected def _asLocalFlowAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: PlaceableFlow](
      flow: F[Flow[V], Remote],
  )(using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)]

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: PlaceableValue](value: F[V, Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): V = _asLocal(value)
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V] =
      _asLocalAll(value)

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: PlaceableFlow](flow: F[Flow[V], Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): Flow[V] = _asLocalFlow(flow)
    def asLocalAll[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)] =
      _asLocalFlowAll(flow)
end Multitier

object Multitier:
  def function[In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: PlaceableValue, Local <: Peer](using
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[Local]],
  )(
      body: mt.MultitierLabel[Local] ?=> In => Out,
  ): mt.PlacedFunction[Local, In, Out, P] = mt.function(body)

  inline def placed[P <: Peer](using
      net: Network,
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]],
  )[V: Encoder, F[_, _ <: Peer]: PlaceableValue](body: mt.MultitierLabel[P] ?=> V): F[V, P] = mt.placed(body)

  inline def multitier[P <: Peer](body: Multitier ?=> Unit)(using Network): Unit =
    given MultitierImpl(peer[P])
    body
end Multitier

//object Test:
//  type Client <: Peer { type Tie <: Single[Server] & Multiple[Client] }
//  type Server <: Peer { type Tie <: Multiple[Client] }
//
//  import Multitier.*
//  import PlacementType.{*, given}
//  import Collective.*
//
//  import io.circe.syntax.*
//  import io.circe.parser.decode as circeDecode
//
//  given [T: {CirceEncoder, CirceDecoder}]: Codec[T] with
//    override def decode(data: Array[Byte]): Either[String, T] = circeDecode(data.mkString).left.map(_.getMessage)
//    override def encode(value: T): Array[Byte] = value.asJson.noSpaces.getBytes
//
//  def placedFunction(using Multitier, Network) = function[(Int, String), Int, on, Server]: (inputs: (Int, String)) =>
//    inputs._1 + inputs._2.length
//
//  def foo(using Multitier, Network): Int on Client = placed():
//    val a = asLocal(placedFunction((12, "Hello")))
//    12
//
//  def chor(using Network, Multitier, Collective)(value: Int on Server): Int on Client = collective:
//    val x = foo
//    x.<@
//
//
//  def bar(using Network, Collective): Int flowOn Client = collective:
//    nbr(10).default
