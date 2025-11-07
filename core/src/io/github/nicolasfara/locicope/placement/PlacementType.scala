package io.github.nicolasfara.locicope.placement

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.network.Network.register
import io.github.nicolasfara.locicope.serialization.Codec
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.network.Network.reachablePeers
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.network.Network.send
import ox.flow.Flow
import ox.{supervised, fork}

object PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placed[V, P]

  protected[locicope] enum Placed[+V, -P <: Peer]:
    case Local(value: V, ref: Reference)
    case Remote(ref: Reference)

  inline def getReference[V, P <: Peer](value: V on P): Reference = value match
    case Placed.Local(_, ref) => ref
    case Placed.Remote(ref) => ref

  trait PeerScope[P <: Peer]

  trait Placement:
    inline def liftF[P <: Peer](using
        Network,
    )[Container[_], Value: Codec](peerRepr: PeerRepr)(value: Option[Container[Value]], ref: Reference): Container[Value] on P =
      value
        .map: value =>
          register(ref, value)
          val peers = reachablePeers[P](peerRepr)
          peers.foreach: address =>
            send(address, ref, value)
          PlacementType.Placed.Local(value, ref)
        .getOrElse:
          PlacementType.Placed.Remote(ref)

end PlacementType
