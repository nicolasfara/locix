package io.github.nicolasfara.locicope

import scala.collection.mutable
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.{ peer, PeerRepr, TiedToMultiple }
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow
import ox.sleep
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.network.Network.{ getId, reachablePeersOf, receive }
import io.github.nicolasfara.locicope.network.Network.localAddress
import io.github.nicolasfara.locicope.network.Network.send
import io.github.nicolasfara.locicope.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.PlacementType

object Collective:
  type Collective = Locicope[Collective.Effect]

  type OutboundMessage = Map[String, Array[Byte]]
  private type InboundMessage[Id] = Map[Id, OutboundMessage]
  private type State = Map[String, Any]

  def repeat[Value](initial: Value)(f: Value => Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.repeat(using vm)(initial)(f)
  def neighbors[Value: Codec](value: Value)(using coll: Collective, vm: coll.effect.VM): vm.Field[Value] =
    coll.effect.neighbors(using vm)(value)
  def branch[Value](condition: Boolean)(ifTrue: => Value)(ifFalse: => Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.branch(using vm)(condition)(ifTrue)(ifFalse)
  def mux[Value](condition: Boolean)(ifTrue: Value)(ifFalse: Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.mux(using vm)(condition)(ifTrue)(ifFalse)
  def localId(using coll: Collective, vm: coll.effect.VM): vm.DeviceId =
    coll.effect.localId(using vm)
  def take[P <: Peer](using coll: Collective, net: Network, scope: PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
    coll.effect.take(using net, scope)(value)

  inline def collective[V: Codec, P <: TiedToMultiple[P]](using
      coll: Collective,
      pf: PlacedFlow,
      net: Network,
      outboundCodec: Codec[OutboundMessage],
  )(every: FiniteDuration = 1.second)(
      block: (coll.effect.VM, PeerScope[P]) ?=> V,
  ): Flow[V] on P =
    val localPeerRepr = peer[P]
    given CollectivePeerScope[P]()
    val resourceId = hashBody(block(using emptyVm(getId(localAddress)), summon[CollectivePeerScope[P]]))
    val referenceOutbound = Reference(s"$resourceId-Outbound", localPeerRepr, NetworkResource.ValueType.Flow)
    val reference = Reference(resourceId, localPeerRepr, NetworkResource.ValueType.Value)
    val flowResult = if coll.effect.localPeerRepr <:< localPeerRepr then
      var lastState: State = Map.empty
      val resultFlow = FlowOps.onEvery(every):
        val neighborMessages = reachablePeersOf[P]
          .map: peerAddress =>
            val neighborMessage = receive[P, P, [X] =>> X, OutboundMessage](peerAddress, referenceOutbound).fold(throw _, identity)
            getId(peerAddress) -> neighborMessage
          .toMap
        val (newValue, newState, exported) = executeRound(using coll)(getId(localAddress), neighborMessages, lastState)(block)
        send[P, P, OutboundMessage](localAddress, referenceOutbound, exported).fold(throw _, identity)
        lastState = newState
        newValue
      Some(resultFlow)
    else None
    summon[PlacedFlow].effect.liftF(localPeerRepr)(flowResult, reference)
  end collective

  inline def run[P <: TiedToMultiple[P]](using Network)[V](inline block: (Collective, PeerScope[P]) ?=> V): Unit =
    val handler = new HandlerImpl[V](peer[P])
    given CollectivePeerScope[P]()
    Locicope.handle(block)(using handler)

  class CollectivePeerScope[P <: TiedToMultiple[P]] extends PeerScope[P]
  class HandlerImpl[V](peerRepr: PeerRepr) extends Locicope.Handler[Collective.Effect, V, Unit]:
    override def handle(program: Locicope[Effect] ?=> V): Unit = program(using new Locicope(EffectImpl(peerRepr)))

  private def executeRound[Id, V](using
      coll: Collective,
  )(id: Id, messages: InboundMessage[Id], state: State)(program: coll.effect.VM ?=> V): (V, State, OutboundMessage) =
    given vm: coll.effect.VM = createVm(using coll)(id, state, messages)
    val result = program(using vm)
    (result, vm.createState, vm.createExport)

  private def emptyVm[Id](using coll: Collective)(deviceId: Id): coll.effect.VM = new coll.effect.VM:
    override type DeviceId = Id
    val localId: Id = deviceId
    def currentPath: String = ""
    def align[V](slot: String)(body: () => V): V = body()
    def stateAt[V](path: String): Option[V] = None
    def setStateAt[V](value: V): Unit = ()
    def neighborsValuesAt[V: Decoder](path: String): Map[DeviceId, V] = Map.empty
    def setValueAt[V: Encoder](value: V): Unit = ()
    def createExport: Map[String, Array[Byte]] = Map.empty
    def createState: Map[String, Any] = Map.empty

  private def createVm(using coll: Collective)[Id](id: Id, state: State, inboundMessage: InboundMessage[Id]): coll.effect.VM = new coll.effect.VM:
    private val stack = mutable.Stack[InvocationCoordinate]()
    private val trace = mutable.Map[String, Int]()
    private val currentState: mutable.Map[String, Any] = mutable.Map()
    private val toSend: mutable.Map[String, Array[Byte]] = mutable.Map()

    type DeviceId = Id

    case class InvocationCoordinate(key: String, invocationCount: Int):
      override def toString: String = s"$key.$invocationCount"

    override val localId: DeviceId = id

    override def currentPath: String = stack.reverse.mkString("/")
    override def align[V](slot: String)(body: () => V): V =
      val invocationCount = trace.get(currentPath).map(_ + 1).getOrElse(0)
      stack.push(InvocationCoordinate(slot, invocationCount))
      val result = body()
      val _ = stack.pop()
      trace.update(currentPath, invocationCount)
      result

    override def stateAt[V](path: String): Option[V] = state.get(path).map(_.asInstanceOf[V])
    override def setStateAt[V](value: V): Unit = currentState.update(currentPath, value)
    override def neighborsValuesAt[V: Decoder as decoder](path: String): Map[Id, V] =
      inboundMessage.flatMap:
        case (id, msg) =>
          msg
            .get(path)
            .map: v =>
              id -> decoder
                .decode(v)
                .fold(
                  ex => throw IllegalStateException(s"Error decoding neighbor value $ex"),
                  identity,
                )
    override def setValueAt[V: Encoder](value: V): Unit = toSend.update(currentPath, summon[Encoder[V]].encode(value))
    override def createExport: Map[String, Array[Byte]] = toSend.toMap
    override def createState: Map[String, Any] = currentState.toMap

  private class EffectImpl(peerRepr: PeerRepr) extends Effect:

    override def take[P <: Peer](using Network, PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
      val PlacementType.Placed.Local[Flow[V] @unchecked, P @unchecked](flow, _) = value.runtimeChecked
      flow

    override protected[locicope] val localPeerRepr: PeerRepr = peerRepr

    override def repeat[Value](using vm: VM)(initial: Value)(f: Value => Value): Value =
      vm.align("repeat"): () =>
        val result = f(vm.stateAt(vm.currentPath).getOrElse(initial))
        vm.setStateAt(result)
        result

    override def neighbors[Value: Codec](using vm: VM)(value: Value): vm.Field[Value] =
      vm.align("neighbors"): () =>
        vm.setValueAt(value)
        vm.Field(value, vm.neighborsValuesAt[Value](vm.currentPath))

    override def branch[Value](using vm: VM)(condition: Boolean)(ifTrue: => Value)(ifFalse: => Value): Value =
      vm.align(s"branch[$condition]"): () =>
        if condition then ifTrue else ifFalse

    override def mux[Value](using VM)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value =
      if condition then ifTrue else ifFalse

    override def localId(using vm: VM): vm.DeviceId = vm.localId
  end EffectImpl

  trait Effect:
    protected[locicope] val localPeerRepr: PeerRepr

    trait VM:
      type DeviceId

      case class Field[V](local: V, overrides: Map[DeviceId, V])

      val localId: DeviceId
      def currentPath: String
      def align[V](slot: String)(body: () => V): V
      def stateAt[V](path: String): Option[V]
      def setStateAt[V](value: V): Unit
      def neighborsValuesAt[V: Decoder](path: String): Map[DeviceId, V]
      def setValueAt[V: Encoder](value: V): Unit
      def createExport: Map[String, Array[Byte]]
      def createState: Map[String, Any]

    def repeat[Value](using VM)(initial: Value)(f: Value => Value): Value
    def neighbors[Value: Codec](using vm: VM)(value: Value): vm.Field[Value]
    def branch[Value](using VM)(condition: Boolean)(ifTrue: => Value)(ifFalse: => Value): Value
    def mux[Value](using VM)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value
    def localId(using vm: VM): vm.DeviceId
    def take[P <: Peer](using Network, PeerScope[P])[V](value: Flow[V] on P): Flow[V]
  end Effect
end Collective
