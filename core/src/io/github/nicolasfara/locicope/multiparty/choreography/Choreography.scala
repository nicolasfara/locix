package io.github.nicolasfara.locicope.multiparty.choreography

import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, Quantifier, TiedToMultiple, TiedToSingle }
import io.github.nicolasfara.locicope.network.{ Network, NetworkResource }
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }

import scala.annotation.targetName
import scala.util.NotGiven

trait Choreography:
  trait ChoreographyLabel[+P <: Peer]

  protected val localPeerRepr: PeerRepr

  inline def at[P <: Peer, V: Encoder, F[_, _ <: Peer]: Placeable](body: ChoreographyLabel[P] ?=> V)(using
      NotGiven[ChoreographyLabel[P]],
      Network,
  ): F[V, P] =
    given ChoreographyLabel[P]()
    val resourceReference = ResourceReference(hashBody(body), localPeerRepr, NetworkResource.ValueType.Value)
    val placedPeerRepr = peer[P]
    if localPeerRepr <:< placedPeerRepr then
      val result = body
      summon[Placeable[F]].lift(Some(result), resourceReference)
    else summon[Placeable[F]].lift(None, resourceReference)

  inline def comm[V: Codec, Sender <: Peer, Receiver <: { type Tie <: Quantifier[Sender] }, F[_, _ <: Peer]: Placeable](
      effect: F[V, Sender],
  )(using Network): F[V, Receiver] = summon[Placeable[F]].comm[V, Sender, Receiver](effect, localPeerRepr)

  extension [V: Decoder, P <: Peer, F[_, _ <: Peer]: Placeable](value: F[V, P])
    def unwrapAll[Local <: TiedToMultiple[P]](using net: Network, cl: ChoreographyLabel[Local]): Map[net.ID, V] =
      summon[Placeable[F]].unliftAll(value)
    def unwrap(using Network, ChoreographyLabel[P]): V =
      summon[Placeable[F]].unlift(value)
end Choreography

object Choreography:
  inline def at[P <: Peer](using
      net: Network,
  )[V: Encoder, F[_, _ <: Peer]: Placeable](using
      choreo: Choreography,
      ng: NotGiven[choreo.ChoreographyLabel[P]],
  )(
      body: choreo.ChoreographyLabel[P] ?=> V,
  ): F[V, P] = choreo.at[P, V, F](body)

  inline def comm[Remote <: Peer, Local <: { type Tie <: Quantifier[Remote] }](using
      choreo: Choreography,
      net: Network,
  )[V: Codec, F[_, _ <: Peer]: Placeable](
      effect: F[V, Remote],
  ): F[V, Local] = choreo.comm[V, Remote, Local, F](effect)

  inline def choreography[P <: Peer](using Network)(body: Choreography ?=> Unit): Unit =
    given ChoreographyImpl(peer[P])
    body
end Choreography
