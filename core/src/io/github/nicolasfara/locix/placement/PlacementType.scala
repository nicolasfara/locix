package io.github.nicolasfara.locix.placement

import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.*
import ox.flow.Flow
import ox.{ fork, supervised }
import scala.caps.Capability

object PlacementType:
  opaque infix type on[+V, -P <: Peer] = Placed[V, P]

  trait MemberOf[Member, Group]
  object MemberOf:
    given [A]: MemberOf[A, A] with {}
    given [A, B, X](using MemberOf[X, A]): MemberOf[X, A & B] with {}

  protected[locix] enum Placed[+V, -P <: Peer]:
    case Local(value: V, ref: Reference[P])
    case Remote(ref: Reference[P])

  inline def getReference[V, P <: Peer](value: V on P): Reference[P] = value match
    case Placed.Local(_, ref) => ref
    case Placed.Remote(ref) => ref

  final class PeerScope[P <: Peer] extends /*compiletime.Erased,*/ Capability
  final class PlacedLabel extends compiletime.Erased, Capability

  trait Placement:
    inline def liftF[From <: Peer: PeerRepr](using
        net: Network,
    )[F[_], Value](value: Option[F[Value]], ref: Reference[From]): F[Value] on From =
      value
        .map: toSend =>
          register[F, Value](ref, toSend)
          toSend match
            case flowValue: Flow[?] =>
              supervised:
                fork:
                  flowValue.runForeach:
                    toSendFlow =>
                      broadcast[From, Value](ref, toSendFlow.asInstanceOf[Value])
                      // sendToPeer[From, To, Value](ref, toSendFlow.asInstanceOf[Value])
                  // reachablePeersOf[To].foreach(terminateFlow[From, To](_, ref))
                  broadcast[From, FlowTermination](ref, FlowTermination())
                Placed.Local(flowValue, ref)
            case plainValue: Value @unchecked =>
              broadcast[From, Value](ref, plainValue)
              // sendToPeer(ref, plainValue)
              Placed.Local(toSend, ref)
        .getOrElse(PlacementType.Placed.Remote(ref))

    // private def sendToPeer[From <: TiedWith[To]: PeerRepr, To <: Peer: PeerRepr, Value](using
    //     net: Network,
    // )(ref: Reference[?], value: Value) =
    //   reachablePeersOf[To].foreach: address =>
    //     send[To, From, Value](address, ref, value)
  end Placement

end PlacementType
