package io.github.nicolasfara.locix.placement

import scala.annotation.nowarn
import scala.util.NotGiven

import io.github.nicolasfara.locix.Locix
import io.github.nicolasfara.locix.macros.ASTHashing.hashBody
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.network.NetworkResource.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import ox.flow.Flow

object PlacedFlow:
  type PlacedFlow = Locix[PlacedFlow.Effect]

  def flowOn[P <: Peer: PeerRepr](using
      pf: PlacedFlow,
      net: Network,
      ng: NotGiven[PeerScope[P]],
  )[Value](expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P =
    pf.effect.flowOn[P, Value](expression)

  extension [Remote <: Peer, Value](using pf: PlacedFlow)(placedFlow: Flow[Value] on Remote)
    def takeFlow(using PeerScope[Remote]): Flow[Value] =
      pf.effect.take(placedFlow)

  def run[P <: Peer: PeerRepr](using net: Network)[V](program: (PlacedFlow, PeerScope[P]) ?=> V): V =
    val handler = new Locix.Handler[PlacedFlow.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effectImplementation[P]))
    given PeerScope[P] = PeerScope[P]()
    Locix.handle(program)(using handler)

  private def effectImplementation[LocalPeer <: Peer: PeerRepr] = new Effect:
    override def take[P <: Peer](using PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
      val PlacementType.Placed.Local[Flow[V] @unchecked, P @unchecked](localValue, _) = value.runtimeChecked
      localValue

    override def flowOn[P <: Peer: PeerRepr, Value](using
        Network,
        NotGiven[PeerScope[P]],
    )(expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P =
      given PeerScope[P] = PeerScope[P]()
      given PlacedLabel = new PlacedLabel
      val peerRepr = summon[PeerRepr[P]]
      val resourceReference = Reference(hashBody(expression), peerRepr, ValueType.Flow)
      val placedFlow = if summon[PeerRepr[LocalPeer]] <:< peerRepr then
        val result = expression
        Some(result)
      else None
      liftF(placedFlow, resourceReference)
  end effectImplementation

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
    def flowOn[P <: Peer: PeerRepr, Value](using
        Network,
        NotGiven[PeerScope[P]],
    )(expression: (PeerScope[P], PlacedLabel) ?=> Flow[Value]): Flow[Value] on P

    def take[P <: Peer](using PeerScope[P])[V](value: Flow[V] on P): Flow[V]
end PlacedFlow
