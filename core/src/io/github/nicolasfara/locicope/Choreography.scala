// package io.github.nicolasfara.locicope

// import io.github.nicolasfara.locicope.Net.{ setValue, Net }
// import io.github.nicolasfara.locicope.PlacementType.{ getLocalValue, on, PeerScope }
// import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
// import io.github.nicolasfara.locicope.network.NetworkResource.Reference
// import io.github.nicolasfara.locicope.network.NetworkResource.ValueType.Value
// import io.github.nicolasfara.locicope.placement.Peers.{ peer, Peer, PeerRepr, TiedWith }
// import io.github.nicolasfara.locicope.serialization.{ Codec, Encoder }

// import scala.util.NotGiven

// object Choreography:
//   type Choreography = Locicope[Choreography.Effect]

//   inline def at[P <: Peer](using Net, Choreography, NotGiven[ChoreoPeerScope[P]])[V: Encoder](body: PeerScope[P] ?=> V): V on P =
//     summon[Choreography].effect.at[V, P](body)(peer[P])

//   inline def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](using
//       Net,
//       Choreography,
//   )(value: V on Sender): V on Receiver =
//     summon[Choreography].effect.comm[V, Sender, Receiver](value)(peer[Sender])

//   inline def run[P <: Peer](using Net)[V](program: Choreography ?=> V): Unit =
//     val handler = ChoreoHandler[V](peer[P])
//     Locicope.handle(program)(using handler)

//   class ChoreoPeerScope[P <: Peer](val peerRepr: PeerRepr) extends PeerScope[P]

//   class ChoreoHandler[V](peerRepr: PeerRepr) extends Locicope.Handler[Choreography.Effect, V, Unit]:
//     override def handle(program: Locicope[Effect] ?=> V): Unit = program(using new Locicope(EffectImpl(peerRepr)))

//   private class EffectImpl(val localPeerRepr: PeerRepr) extends Effect:
//     override def at[V: Encoder, P <: Peer](body: ChoreoPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net, NotGiven[ChoreoPeerScope[P]]): V on P =
//       given ChoreoPeerScope[P](peerRepr)
//       val resourceReference = Reference(hashBody(body), localPeerRepr, Value)
//       val placementValue = if peerRepr <:< localPeerRepr then
//         val result = body
//         Some(result)
//       else None
//       PlacementType.lift(placementValue, resourceReference)

//     override def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](value: V on Sender)(senderPeerRepr: PeerRepr)(using
//         Net,
//     ): V on Receiver =
//       val ref = PlacementType.getRef(value)
//       val placedValue = if senderPeerRepr <:< localPeerRepr then
//         val localValue = getLocalValue(value) match
//           case Some(value) => value
//           case None => throw IllegalStateException("Please fill a bug report, something went wrong during `comm`.")
//         setValue(localValue, ref)
//         Some(localValue)
//       else None
//       PlacementType.lift(placedValue, ref)
//   end EffectImpl

//   trait Effect:
//     protected[locicope] val localPeerRepr: PeerRepr

//     def at[V: Encoder, P <: Peer](body: ChoreoPeerScope[P] ?=> V)(peerRepr: PeerRepr)(using Net, NotGiven[ChoreoPeerScope[P]]): V on P
//     def comm[V: Codec, Sender <: Peer, Receiver <: TiedWith[Sender]](value: V on Sender)(senderPeerRepr: PeerRepr)(using Net): V on Receiver
// end Choreography
