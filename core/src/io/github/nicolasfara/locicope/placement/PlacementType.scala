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

object PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placed[V, P]

  protected[locicope] enum Placed[+V, -P <: Peer]:
    case Local(value: V, ref: Reference)
    case Remote(ref: Reference)

  trait PeerScope[P <: Peer]

  trait Placement:
    def lift[P <: Peer](using Network)[Value: Codec](peerRepr: PeerRepr)(value: Option[Value], ref: Reference): Value on P =
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
