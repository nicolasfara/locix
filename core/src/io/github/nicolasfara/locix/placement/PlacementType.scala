package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.serialization.Codec
import ox.flow.Flow
import ox.{ fork, supervised }

object PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placed[V, P]

  protected[locix] enum Placed[+V, -P <: Peer]:
    case Local(value: V, ref: Reference)
    case Remote(ref: Reference)

  inline def getReference[V, P <: Peer](value: V on P): Reference = value match
    case Placed.Local(_, ref) => ref
    case Placed.Remote(ref) => ref

  trait PeerScope[P <: Peer]

  trait Placement:
    inline def liftF[P <: Peer, Other <: TiedWith[P]](using
        Network,
    )[F[_], Value: Codec](peerRepr: PeerRepr)(value: Option[F[Value]], ref: Reference): F[Value] on P =
      value
        .map: toSend =>
          register[F, Value](ref, toSend)
          toSend match
            case flowValue: Flow[?] =>
              supervised:
                fork:
                  flowValue.runForeach: toSendFlow =>
                    sendToPeer(peerRepr, ref, toSendFlow.asInstanceOf[Value])
                  reachablePeers[P](peerRepr).foreach(terminateFlow(_, ref))
                Placed.Local(flowValue, ref)
            case plainValue: Value @unchecked =>
              sendToPeer[P, Other, Value](peerRepr, ref, plainValue)
              Placed.Local(toSend, ref)
        .getOrElse(PlacementType.Placed.Remote(ref))

    private def sendToPeer[P <: Peer, Other <: TiedWith[P], Value: Codec](using
        net: Network,
    )(peerRepr: PeerRepr, ref: Reference, value: Value) =
      reachablePeers[P](peerRepr).foreach: address =>
        send[P, Other, Value](address, ref, value)

      // value
      //   .map: value =>
      //     register(ref, value)
      //     val peers = reachablePeers[P](peerRepr)
      //     peers.foreach: address =>
      //       send(address, ref, value)
      //     PlacementType.Placed.Local(value, ref)
      //   .getOrElse:
      //     PlacementType.Placed.Remote(ref)
  end Placement

end PlacementType
