package io.github.locicope.multiparty.multitier

import io.circe.{Decoder as CirceDecoder, Encoder as CirceEncoder}
import io.github.locicope.placement.Peers.Quantifier.{Multiple, Single}
import io.github.locicope.placement.Peers.{Peer, PeerRepr, TiedToMultiple, TiedToSingle}
import io.github.locicope.network.Network
import io.github.locicope.serialization.{Codec, Decoder, Encoder}
import io.github.locicope.placement.{Flowable, Placeable}
import ox.flow.Flow

import scala.util.NotGiven

trait Multitier:
  trait MultitierLabel[+P <: Peer]

  trait PlacedFunction[Local <: Peer, In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable]:
    protected val localPeerRepr: PeerRepr
    def apply(inputs: In): P[Out, Local]

  def function[In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable, Local <: Peer](
      body: MultitierLabel[Local] ?=> In => Out
  )(using NotGiven[MultitierLabel[Local]]): PlacedFunction[Local, In, Out, P]

  def placed[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](
      deps: PlacedFunction[?, ?, ?, F]*
  )(body: MultitierLabel[P] ?=> V)(using
      NotGiven[MultitierLabel[P]],
      Network
  ): F[V, P]

  protected def `asLocal@`[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: Placeable](
      effect: F[V, Remote]
  )(using Network, MultitierLabel[Local]): V

  def asLocalAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: Placeable](
      effect: F[V, Remote]
  )(using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V]

  def asLocalFlow[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: Flowable](
      flow: F[Flow[V], Remote]
  )(using Network, MultitierLabel[Local]): Flow[V]

  def asLocalFlowAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: Flowable](
      flow: F[Flow[V], Remote]
  )(using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)]

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Placeable](value: F[V, Remote])
    def asLocal[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): V = `asLocal@`(value)
    def <<@[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Map[net.ID, V] =
      asLocalAll(value)

  extension [V: Decoder, Remote <: Peer, F[_, _ <: Peer]: Flowable](flow: F[Flow[V], Remote])
    def <~[Local <: TiedToSingle[Remote]](using Network, MultitierLabel[Local]): Flow[V] = asLocalFlow(flow)
    def <<~[Local <: TiedToMultiple[Remote]](using net: Network, ml: MultitierLabel[Local]): Flow[(net.ID, V)] =
      asLocalFlowAll(flow)

object Multitier:
  def function[In <: Product: Codec, Out: Encoder, P[_, _ <: Peer]: Placeable, Local <: Peer](using
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[Local]]
  )(
      body: mt.MultitierLabel[Local] ?=> In => Out
  ): mt.PlacedFunction[Local, In, Out, P] = mt.function(body)

  def placed[V: Encoder, P <: Peer, F[_, _ <: Peer]: Placeable](using
      net: Network,
      mt: Multitier,
      ng: NotGiven[mt.MultitierLabel[P]]
  )(
      deps: mt.PlacedFunction[?, ?, ?, F]*
  )(
      body: mt.MultitierLabel[P] ?=> V
  ): F[V, P] = mt.placed(deps*)(body)

  def asLocal[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], P[_, _ <: Peer]: Placeable](
      effect: P[V, Remote]
  )(using
      net: Network,
      mt: Multitier,
      ml: mt.MultitierLabel[Local]
  ): V = mt.asLocal(effect)

  def asLocalAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: Placeable](
      effect: F[V, Remote]
  )(using net: Network, mt: Multitier, ml: mt.MultitierLabel[Local]): Map[net.ID, V] = mt.asLocalAll(effect)

  def asLocalFlow[V: Decoder, Remote <: Peer, Local <: TiedToSingle[Remote], F[_, _ <: Peer]: Flowable](
      flow: F[Flow[V], Remote]
  )(using net: Network, mt: Multitier, ml: mt.MultitierLabel[Local]): Flow[V] = mt.asLocalFlow(flow)

  def asLocalFlowAll[V: Decoder, Remote <: Peer, Local <: TiedToMultiple[Remote], F[_, _ <: Peer]: Flowable](
      flow: F[Flow[V], Remote]
  )(using net: Network, mt: Multitier, ml: mt.MultitierLabel[Local]): Flow[(net.ID, V)] = mt.asLocalFlowAll(flow)

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
