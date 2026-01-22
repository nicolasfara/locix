package io.github.nicolasfara.locix

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

import io.github.nicolasfara.locix.macros.ASTHashing.hashBody
import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.network.NetworkResource
import io.github.nicolasfara.locix.network.NetworkResource.*
import io.github.nicolasfara.locix.placement.Peers.*
import io.github.nicolasfara.locix.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.*
import ox.flow.Flow
import scala.caps.Capability
import cats.Id

object Collective:
  type Collective = Locix[Collective.Effect]

  type OutboundMessage = Map[String, Any]
  private type InboundMessage[Id] = Map[Id, OutboundMessage]
  private type State = Map[String, Any]

  def repeat[Value](initial: Value)(f: Value => Value)(using coll: Collective, vm: coll.effect.VM^): Value =
    coll.effect.repeat(using vm)(initial)(f)
  def neighbors[Value](value: Value)(using coll: Collective, vm: coll.effect.VM^): vm.Field[Value] =
    coll.effect.neighbors(using vm)(value)
  def branch[Value](condition: Boolean)(ifTrue: -> Value)(ifFalse: -> Value)(using coll: Collective, vm: coll.effect.VM^): Value =
    coll.effect.branch(using vm)(condition)(ifTrue)(ifFalse)
  def mux[Value](condition: Boolean)(ifTrue: Value)(ifFalse: Value)(using coll: Collective, vm: coll.effect.VM^): Value =
    coll.effect.mux(using vm)(condition)(ifTrue)(ifFalse)
  def localId(using coll: Collective, vm: coll.effect.VM^): vm.DeviceId =
    coll.effect.localId(using vm)
  def take[P <: Peer](using coll: Collective, net: Network, scope: PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
    coll.effect.take(using net, scope)(value)

  def collective[P <: TiedToMultiple[P]: PeerRepr](using
      coll: Collective,
      pf: PlacedFlow,
      net: Network,
  )[V](every: FiniteDuration)(
      block: (coll.effect.VM^, PeerScope[P]) ?-> V,
  ): Flow[V] on P =
    val localPeerRepr = summon[PeerRepr[P]]
    given ps: PeerScope[P] = PeerScope[P]()
    val resourceId = hashBody(block(using emptyVm(getId(localAddress)), ps))
    val referenceOutbound = Reference(s"$resourceId-Outbound", localPeerRepr, NetworkResource.ValueType.Value)
    val reference = Reference(resourceId, localPeerRepr, NetworkResource.ValueType.Flow)
    val flowResult = if coll.effect.localPeerRepr <:< localPeerRepr then
      var lastState: State = Map.empty
      val resultFlow = FlowOps.onEvery(every):
        val neighborMessages = reachablePeersOf[P]
          .map: peerAddress =>
            val neighborMessage = receive[P, P, Id, OutboundMessage](peerAddress, referenceOutbound).fold(_ => Map.empty, identity)
            getId(peerAddress) -> neighborMessage
          .toMap
        val (newValue, newState, exported) = executeRound(using coll)(getId(localAddress), neighborMessages, lastState)(block)
        reachablePeersOf[P].foreach: peerAddress =>
          send[P, P, OutboundMessage](peerAddress, referenceOutbound, exported).fold(throw _, identity)
        lastState = newState
        newValue
      Some(resultFlow)
    else None
    pf.effect.liftF(flowResult, reference)
  end collective

  def run[P <: TiedToMultiple[P]: PeerRepr](using Network)[V](program: Collective ?=> V): V =
    val effect = effectImpl[P]
    val handler = new Locix.Handler[Collective.Effect, V, V]:
      override def handle(program: (Locix[Effect]) ?=> V): V = program(using Locix(effect))
    given PeerScope[P] = PeerScope[P]()
    Locix.handle(program)(using handler)

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
    def neighborsValuesAt[V](path: String): Map[DeviceId, V] = Map.empty
    def setValueAt[V](value: V): Unit = ()
    def createExport: Map[String, Array[Byte]] = Map.empty
    def createState: Map[String, Any] = Map.empty

  private def createVm(using coll: Collective)[Id](id: Id, state: State, inboundMessage: InboundMessage[Id]): coll.effect.VM = new coll.effect.VM:
    private val stack = mutable.Stack[InvocationCoordinate]()
    private val trace = mutable.Map[String, Int]()
    private val currentState: mutable.Map[String, Any] = mutable.Map()
    private val toSend: mutable.Map[String, Any] = mutable.Map()

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
    override def neighborsValuesAt[V](path: String): Map[Id, V] =
      inboundMessage.flatMap:
        case (id, msg) =>
          msg
            .get(path)
            .map: v =>
              id -> v.asInstanceOf[V]
    override def setValueAt[V](value: V): Unit = toSend.update(currentPath, value)
    override def createExport: Map[String, Any] = toSend.toMap
    override def createState: Map[String, Any] = currentState.toMap

  private def effectImpl[LP <: Peer : PeerRepr] = new Effect:

    protected[locix] type LocalPeer = LP
    protected[locix] val localPeerRepr: PeerRepr[LocalPeer] = summon[PeerRepr[LP]]

    override def take[P <: Peer](using Network, PeerScope[P])[V](value: Flow[V] on P): Flow[V] =
      val PlacementType.Placed.Local[Flow[V] @unchecked, P @unchecked](flow, _) = value.runtimeChecked
      flow

    override def repeat[Value](using vm: VM^)(initial: Value)(f: Value => Value): Value =
      vm.align("repeat"): () =>
        val result = f(vm.stateAt(vm.currentPath).getOrElse(initial))
        vm.setStateAt(result)
        result

    override def neighbors[Value](using vm: VM^)(value: Value): vm.Field[Value] =
      vm.align("neighbors"): () =>
        vm.setValueAt(value)
        vm.Field(value, vm.neighborsValuesAt[Value](vm.currentPath))

    override def branch[Value](using vm: VM^)(condition: Boolean)(ifTrue: -> Value)(ifFalse: -> Value): Value =
      vm.align(s"branch[$condition]"): () =>
        if condition then ifTrue else ifFalse

    override def mux[Value](using VM^)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value =
      if condition then ifTrue else ifFalse

    override def localId(using vm: VM^): vm.DeviceId = vm.localId

  trait Effect:
    protected[locix] type LocalPeer <: Peer
    protected[locix] val localPeerRepr: PeerRepr[LocalPeer]
    trait VM:
      type DeviceId

      case class Field[V](local: V, overrides: Map[DeviceId, V])

      val localId: DeviceId
      def currentPath: String
      def align[V](slot: String)(body: () => V): V
      def stateAt[V](path: String): Option[V]
      def setStateAt[V](value: V): Unit
      def neighborsValuesAt[V](path: String): Map[DeviceId, V]
      def setValueAt[V](value: V): Unit
      def createExport: Map[String, Any]
      def createState: Map[String, Any]

    def repeat[Value](using VM^)(initial: Value)(f: Value => Value): Value
    def neighbors[Value](using vm: VM^)(value: Value): vm.Field[Value]
    def branch[Value](using VM^)(condition: Boolean)(ifTrue: -> Value)(ifFalse: -> Value): Value
    def mux[Value](using VM^)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value
    def localId(using vm: VM^): vm.DeviceId
    def take[P <: Peer](using Network, PeerScope[P])[V](value: Flow[V] on P): Flow[V]
  end Effect
end Collective
