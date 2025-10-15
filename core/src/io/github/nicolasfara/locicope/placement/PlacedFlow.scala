package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.TiedToSingle
import io.github.nicolasfara.locicope.placement.Peers.TiedToMultiple
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.network.Network.{ reachablePeers, receive, register }
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.placement.PlacementType.{ on, PeerScope, Placement }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import ox.flow.Flow

object PlacedFlow:
  type PlacedFlow = Locicope[PlacedFlow.Effect]

  class PlacedFlowPeerScope[P <: Peer] extends PeerScope[P]

  inline def flowOn[P <: Peer](using pf: PlacedFlow, net: Network)[Value: Codec](expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P =
    pf.effect.flowOn[P, Value](peer[P])(expression)

  extension [Remote <: Peer, Value: Codec](using pf: PlacedFlow)(placedFlow: Flow[Value] on Remote)
    def unwrap(using PeerScope[Remote]): Flow[Value] =
      pf.effect.unwrap(placedFlow)

    inline def asLocal[Local <: TiedToSingle[Remote]](using PeerScope[Local], Network): Flow[Value] =
      pf.effect.asLocal[Remote, Local, Value](peer[Remote])(placedFlow)

    inline def asLocalAll[Local <: TiedToMultiple[Remote]](using ps: PeerScope[Local], net: Network): Flow[(net.effect.Id, Value)] =
      pf.effect.asLocalAll[Remote, Local, Value](peer[Remote])(placedFlow)

  inline def run[P <: Peer](using net: Network)[V: Codec](program: (PlacedFlow, PeerScope[P]) ?=> V): V =
    given handler: Locicope.Handler[PlacedFlow.Effect, V, V] = new HandlerImpl(peer[P])
    given PeerScope[P] = PlacedFlowPeerScope[P]
    Locicope.handle(program)(using handler)

  class HandlerImpl[Value: Codec](executionPeerRepr: PeerRepr) extends Locicope.Handler[PlacedFlow.Effect, Value, Value]:
    override def handle(program: Locicope[Effect] ?=> Value): Value = program(using new Locicope(EffectImpl(executionPeerRepr)))

  private class EffectImpl(executionPeerRepr: PeerRepr) extends Effect:
    override def flowOn[P <: Peer, Value: Codec](using
        Network,
    )(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P =
      given PeerScope[P] = new PlacedFlowPeerScope[P]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Flow)
      val placedFlow = if executionPeerRepr <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(executionPeerRepr)(placedFlow, resourceReference)

    override def unwrap[Local <: Peer, Value: Codec](using PeerScope[Local])(placedFlow: Flow[Value] on Local): Flow[Value] =
      // https://dotty.epfl.ch/docs/reference/other-new-features/runtimeChecked.html#example
      placedFlow.runtimeChecked match
        case PlacementType.Placed.Local[Flow[Value] @unchecked, Local @unchecked](flow, _) => flow

    override def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote], Value: Codec](using
        ps: PeerScope[Local],
        net: Network,
    )(remotePeerRepr: PeerRepr)(placedFlow: Flow[Value] on Remote): Flow[Value] =
      val peers = reachablePeers[Remote](remotePeerRepr)
      if peers.size != 1 then throw IllegalStateException(s"Expected exactly one remote peer, found ${peers.size}.")
      val address = peers.head
      val PlacementType.Placed.Remote(reference) = placedFlow.runtimeChecked
      receive[Flow, Value, Remote, Local](address, reference).fold(error => throw error, identity)

    override def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], Value: Codec](using
        ps: PeerScope[Local],
        net: Network,
    )(remotePeerRepr: PeerRepr)(placedFlow: Flow[Value] on Remote): Flow[(net.effect.Id, Value)] =
      val PlacementType.Placed.Remote(reference) = placedFlow.runtimeChecked
      val peers = reachablePeers[Remote](remotePeerRepr)
      peers
        .map: peerAddress =>
          receive[Flow, Value, Remote, Local](peerAddress, reference).fold(
            err => throw err,
            flow => flow.map(value => (net.effect.getId(peerAddress), value)),
          )
        .foldLeft(Flow.empty[(net.effect.Id, Value)])(_.merge(_))
  end EffectImpl

  trait Effect extends Placement:
    /**
     * Create a flow on a specific peer. The flow will be executed on the specified peer and can be accessed remotely by other peers.
     *
     * @param peerRepr
     *   the representation of the peer where the flow should be created
     * @param expression
     *   the expression that creates the flow, executed within the peer's scope
     * @return
     *   a placed flow that can be accessed from other peers
     */
    def flowOn[P <: Peer, Value: Codec](using Network)(peerRepr: PeerRepr)(expression: PeerScope[P] ?=> Flow[Value]): Flow[Value] on P

    /**
     * Extract the placed flow. This method can only be called by the peer that owns the flow. Any attempt to call this method on a placed flow on a
     * remote peer will result in a compile-time error.
     *
     * @param placedFlow
     *   the placed flow to extract the flow from.
     * @return
     *   the flow wrapped in the placed flow.
     */
    def unwrap[Local <: Peer, Value: Codec](using PeerScope[Local])(placedFlow: Flow[Value] on Local): Flow[Value]

    /**
     * Given a placed flow on a remote peer tied with the local peer, create a local flow that receives values from the remote flow. This method can
     * only be called if the local peer is tied to a single remote peer. Any call to this method on a placed flow that is not on a remote peer or not
     * tied with the local peer will result in a compile-time error.
     *
     * @param placedFlow
     *   the placed flow to receive values from.
     * @return
     *   a flow that emits values from the single remote flow instance.
     */
    def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote], Value: Codec](using
        ps: PeerScope[Local],
        net: Network,
    )(remotePeerRepr: PeerRepr)(placedFlow: Flow[Value] on Remote): Flow[Value]

    /**
     * Given a placed flow on multiple remote peers tied with the local peer, create a flow that emits values from all instances of the remote flows.
     * Each emitted value is paired with the ID of the remote peer that produced it. This method can only be called if the local peer is tied to
     * multiple remote peers. Any call to this method on a placed flow that is not on a remote peer or not tied with the local peer will result in a
     * compile-time error.
     *
     * @param placedFlow
     *   the placed flow to receive values from.
     * @return
     *   a flow that emits tuples of (peer ID, value) for all values from all remote flow instances.
     */
    def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], Value: Codec](using
        ps: PeerScope[Local],
        net: Network,
    )(remotePeerRepr: PeerRepr)(placedFlow: Flow[Value] on Remote): Flow[(net.effect.Id, Value)]
  end Effect
end PlacedFlow
