package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.*
import ox.flow.Flow
import ox.{ fork, supervised }
import scala.caps.Capability

object PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placed[V, P]

  protected[locix] enum Placed[+V, -P <: Peer]:
    case Local(value: V, ref: Reference[P])
    case Remote(ref: Reference[P])

  inline def getReference[V, P <: Peer](value: V on P): Reference[P] = value match
    case Placed.Local(_, ref) => ref
    case Placed.Remote(ref) => ref

  final class PeerScope[P <: Peer] extends /*compiletime.Erased,*/ Capability
  final class PlacedLabel extends compiletime.Erased, Capability

  trait Placement:
    inline def liftF[P <: Peer: PeerRepr, Other <: TiedWith[P]](using
        Network,
    )[F[_], Value](value: Option[F[Value]], ref: Reference[P]): F[Value] on P =
      value
        .map: toSend =>
          register[F, Value](ref, toSend)
          toSend match
            case flowValue: Flow[?] =>
              supervised:
                fork:
                  flowValue.runForeach: toSendFlow =>
                    sendToPeer(ref, toSendFlow.asInstanceOf[Value])
                  reachablePeersOf[P].foreach(terminateFlow(_, ref))
                Placed.Local(flowValue, ref)
            case plainValue: Value @unchecked =>
              sendToPeer(ref, plainValue)
              Placed.Local(toSend, ref)
        .getOrElse(PlacementType.Placed.Remote(ref))

    private def sendToPeer[P <: Peer: PeerRepr, Other <: TiedWith[P], Value](using
        net: Network,
    )(ref: Reference[Other], value: Value) =
      reachablePeersOf[P].foreach: address =>
        send[P, Other, Value](address, ref, value)
  end Placement

end PlacementType
