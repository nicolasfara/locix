package io.github.nicolasfara.locix

import scala.annotation.nowarn

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.*
import ox.flow.Flow

object Multitier:
  type Multitier = Locix[Multitier.Effect]

  inline def asLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote], V](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedValue: V on Remote): V =
    mt.effect.asLocal(placedValue)

  inline def asLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote], V](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedValue: V on Remote): Map[net.effect.Id, V] =
    mt.effect.asLocalAll(placedValue)

  inline def collectAsLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote], V](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedFlow: Flow[V] on Remote): Flow[V] =
    mt.effect.collectAsLocal(placedFlow)

  inline def collectAsLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote], V](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedFlow: Flow[V] on Remote): Flow[(net.effect.Id, V)] =
    mt.effect.collectAsLocalAll(placedFlow)

  def run[P <: Peer: PeerRepr](using Network)[V](program: Multitier ?=> V): V =
    val effect = effectImpl[P]
    val handler = new Locix.Handler[Multitier.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effect))
    Locix.handle(program)(using handler)

  private def effectImpl[LocalPeer <: Peer: PeerRepr] = new Effect:
    override def asLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V](placedValue: V on Remote): V = placedValue match
      case PlacementType.Placed.Local[V @unchecked, Remote @unchecked](localValue, _) => localValue
      case PlacementType.Placed.Remote[V @unchecked, Remote @unchecked](reference) =>
        val reachablePeer = reachablePeersOf[Remote]
        require(reachablePeer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${reachablePeer}")
        receive[Remote, Local, [X] =>> X, V](reachablePeer.head, reference).fold(throw _, identity)

    override def asLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V](placedValue: V on Remote): Map[net.effect.Id, V] = placedValue match
      case PlacementType.Placed.Local[V @unchecked, Remote @unchecked](localValue, _) =>
        Map(getId(localAddress) -> localValue)
      case PlacementType.Placed.Remote[V @unchecked, Remote @unchecked](reference) =>
        val remoteReachablePeers = reachablePeersOf[Remote]
        remoteReachablePeers
          .map: peerAddress =>
            val receivedValue = receive[Remote, Local, [X] =>> X, V](peerAddress, reference).fold(throw _, identity)
            (getId(peerAddress), receivedValue)
          .toMap

    override def collectAsLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V](placedFlow: Flow[V] on Remote): Flow[V] = placedFlow match
      case PlacementType.Placed.Local[Flow[V] @unchecked, Remote @unchecked](flow, _) => flow
      case PlacementType.Placed.Remote[Flow[V] @unchecked, Remote @unchecked](reference) =>
        val reachablePeer = reachablePeersOf[Remote]
        require(reachablePeer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${reachablePeer}")
        receive[Remote, Local, Flow, V](reachablePeer.head, reference).fold(throw _, identity)

    override def collectAsLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V](placedValue: Flow[V] on Remote): Flow[(net.effect.Id, V)] =
      placedValue match
        case PlacementType.Placed.Local[Flow[V] @unchecked, Remote @unchecked](flow, _) =>
          flow.map(value => (getId(localAddress), value))
        case PlacementType.Placed.Remote[Flow[V] @unchecked, Remote @unchecked](reference) =>
          val remoteReachablePeers = reachablePeersOf[Remote]
          val flows = remoteReachablePeers
            .map: peerAddress =>
              receive[Remote, Local, Flow, V](peerAddress, reference)
                .fold(throw _, identity)
                .map((net.effect.getId(peerAddress), _))
          flows.fold(Flow.empty)(_.merge(_))

  trait Effect:
    def asLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V](placedValue: V on Remote): V

    def asLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V](placedValue: V on Remote): Map[net.effect.Id, V]

    def collectAsLocal[Remote <: Peer: PeerRepr, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V](placedValue: Flow[V] on Remote): Flow[V]

    def collectAsLocalAll[Remote <: Peer: PeerRepr, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V](placedValue: Flow[V] on Remote): Flow[(net.effect.Id, V)]
  end Effect
end Multitier
