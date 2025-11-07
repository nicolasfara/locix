package io.github.nicolasfara.locicope

import scala.annotation.nowarn

import io.github.nicolasfara.locicope.placement.Peers.TiedToSingle
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.{ getId, reachablePeersOf, reachablePeers, Network }
import io.github.nicolasfara.locicope.placement.Peers.TiedToMultiple
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.placement.PlacementType
import io.github.nicolasfara.locicope.network.Network.receive
import io.github.nicolasfara.locicope.network.Network.localAddress
import ox.flow.Flow
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue

object Multitier:
  type Multitier = Locicope[Multitier.Effect]

  inline def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote], V: Codec](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedValue: V on Remote): V =
    mt.effect.asLocal(peer[Remote])(placedValue)

  inline def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], V: Codec](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedValue: V on Remote): Map[net.effect.Id, V] =
    mt.effect.asLocalAll(peer[Remote])(placedValue)

  inline def collectAsLocal[Remote <: Peer, Local <: TiedToSingle[Remote], V: Codec](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedFlow: Flow[V] on Remote): Flow[V] =
    mt.effect.collectAsLocal(peer[Remote])(placedFlow)

  inline def collectAsLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote], V: Codec](using
      mt: Multitier,
      net: Network,
      scope: PeerScope[Local],
  )(placedFlow: Flow[V] on Remote): Flow[(net.effect.Id, V)] =
    mt.effect.collectAsLocalAll(peer[Remote])(placedFlow)

  @nowarn inline def run[P <: Peer](using Network)[V](program: Multitier ?=> V): V =
    val effect = effectImpl[P]()
    val handler = new Locicope.Handler[Multitier.Effect, V, V]:
      override def handle(program: (Locicope[Effect]) ?=> V): V = program(using Locicope(effect))
    Locicope.handle(program)(using handler)

  @nowarn inline private def effectImpl[LocalPeer <: Peer]() = new Effect:
    override def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: V on Remote): V = placedValue match
      case PlacementType.Placed.Local[V @unchecked, Remote @unchecked](localValue, _) => localValue
      case PlacementType.Placed.Remote[V @unchecked, Remote @unchecked](reference) =>
        val reachablePeer = reachablePeers[Remote](remotePeerRepr)
        require(reachablePeer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${reachablePeer}")
        receive[[X] =>> X, V, Remote, Local](reachablePeer.head, reference).fold(throw _, identity)

    override def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: V on Remote): Map[net.effect.Id, V] = placedValue match
      case PlacementType.Placed.Local[V @unchecked, Remote @unchecked](localValue, _) =>
        Map(getId(localAddress) -> localValue)
      case PlacementType.Placed.Remote[V @unchecked, Remote @unchecked](reference) =>
        val remoteReachablePeers = reachablePeers[Remote](remotePeerRepr)
        remoteReachablePeers
          .map: peerAddress =>
            val receivedValue = receive[[X] =>> X, V, Remote, Local](peerAddress, reference).fold(throw _, identity)
            (getId(peerAddress), receivedValue)
          .toMap

    override def collectAsLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedFlow: Flow[V] on Remote): Flow[V] = placedFlow match
      case PlacementType.Placed.Local[Flow[V] @unchecked, Remote @unchecked](flow, _) => flow
      case PlacementType.Placed.Remote[Flow[V] @unchecked, Remote @unchecked](reference) =>
        val reachablePeer = reachablePeers[Remote](remotePeerRepr)
        require(reachablePeer.size == 1, s"Only 1 peer should be connected to this local peer, but found ${reachablePeer}")
        receive[Flow, V, Remote, Local](reachablePeer.head, reference).fold(throw _, identity)

    override def collectAsLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: Flow[V] on Remote): Flow[(net.effect.Id, V)] =
      placedValue match
        case PlacementType.Placed.Local[Flow[V] @unchecked, Remote @unchecked](flow, _) =>
          flow.map(value => (getId(localAddress), value))
        case PlacementType.Placed.Remote[Flow[V] @unchecked, Remote @unchecked](reference) =>
          val remoteReachablePeers = reachablePeers[Remote](remotePeerRepr)
          val flows = remoteReachablePeers
            .map: peerAddress =>
              receive[Flow, V, Remote, Local](peerAddress, reference)
                .fold(throw _, identity)
                .map((net.effect.getId(peerAddress), _))
          flows.fold(Flow.empty)(_.merge(_))

  trait Effect:
    def asLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: V on Remote): V

    def asLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: V on Remote): Map[net.effect.Id, V]

    def collectAsLocal[Remote <: Peer, Local <: TiedToSingle[Remote]](using
        Network,
        PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: Flow[V] on Remote): Flow[V]

    def collectAsLocalAll[Remote <: Peer, Local <: TiedToMultiple[Remote]](using
        net: Network,
        scope: PeerScope[Local],
    )[V: Codec](remotePeerRepr: PeerRepr)(placedValue: Flow[V] on Remote): Flow[(net.effect.Id, V)]
  end Effect
end Multitier
