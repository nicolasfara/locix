package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Net.{ getValues, setValue, Net }
import io.github.nicolasfara.locicope.PlacementType.{ on, PeerScope }
import io.github.nicolasfara.locicope.macros.ASTHashing.hashBody
import io.github.nicolasfara.locicope.network.NetworkResource
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.placement.Peers.{ peer, PeerRepr, TiedToMultiple }
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import ox.flow.Flow

import scala.collection.mutable
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object Collective:
  type Collective = Locicope[Collective.Effect]

  type OutboundMessage = Map[String, Array[Byte]]
  private type InboundMessage = Map[Int, OutboundMessage]
  private type State = Map[String, Any]

  def repeat[Value](initial: Value)(f: Value => Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.repeat(using vm)(initial)(f)
  def neighbors[Value: Codec](value: Value)(using coll: Collective, vm: coll.effect.VM): coll.effect.Field[Value] =
    coll.effect.neighbors(using vm)(value)
  def branch[Value](condition: Boolean)(ifTrue: => Value)(ifFalse: => Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.branch(using vm)(condition)(ifTrue)(ifFalse)
  def mux[Value](condition: Boolean)(ifTrue: Value)(ifFalse: Value)(using coll: Collective, vm: coll.effect.VM): Value =
    coll.effect.mux(using vm)(condition)(ifTrue)(ifFalse)

  inline def collective[V: Codec, P <: TiedToMultiple[P]](using
      coll: Collective,
      net: Net,
      outboundCodec: Codec[OutboundMessage],
  )(every: FiniteDuration = 1.second)(
      block: coll.effect.VM ?=> V,
  ): Flow[V] on P =
    val localPeerRepr = peer[P]
    val resourceReference = ResourceReference(hashBody(block(using emptyVm)), localPeerRepr, NetworkResource.ValueType.Value)
    val flowResult = if coll.effect.localPeerRepr <:< localPeerRepr then
      var lastState: State = Map.empty
      val resultFlow = Flow.repeatEval({
        val neighborMessages = getValues[OutboundMessage](resourceReference).toTry.fold(ex => throw ex, identity)
        val (newValue, newState, exported) = executeRound(using coll)(neighborMessages, lastState)(block)
        setValue[OutboundMessage](exported, resourceReference)
        lastState = newState
        newValue
      })
      Some(resultFlow)
    else None
    PlacementType.liftFlow(flowResult, resourceReference)
  end collective

  inline def run[P <: TiedToMultiple[P]](using Net)[V](inline block: (Collective, PeerScope[P]) ?=> V): Unit =
    val handler = new HandlerImpl[V](peer[P])
    given CollectivePeerScope[P]()
    Locicope.handle(block)(using handler)

  class CollectivePeerScope[P <: TiedToMultiple[P]] extends PeerScope[P]
  class HandlerImpl[V](peerRepr: PeerRepr) extends Locicope.Handler[Collective.Effect, V, Unit]:
    override def handle(program: Locicope[Effect] ?=> V): Unit = program(using new Locicope(EffectImpl(peerRepr)))

  private def executeRound[V](using
      coll: Collective,
  )(messages: InboundMessage, state: State)(program: coll.effect.VM ?=> V): (V, State, OutboundMessage) =
    given vm: coll.effect.VM = createVm(using coll)(state, messages)
    val result = program(using vm)
    (result, vm.createState, vm.createExport)

  private def emptyVm(using coll: Collective): coll.effect.VM = new coll.effect.VM:
    def currentPath: String = ""
    def align[V](slot: String)(body: () => V): V = body()
    def stateAt[V](path: String): Option[V] = None
    def setStateAt[V](value: V): Unit = ()
    def neighborsValuesAt[V: Decoder](path: String): Map[Int, V] = Map.empty
    def setValueAt[V: Encoder](value: V): Unit = ()
    def createExport: Map[String, Array[Byte]] = Map.empty
    def createState: Map[String, Any] = Map.empty

  private def createVm(using coll: Collective)(state: State, inboundMessage: InboundMessage): coll.effect.VM = new coll.effect.VM:
    private val stack = mutable.Stack[InvocationCoordinate]()
    private val trace = mutable.Map[String, Int]()
    private val currentState: mutable.Map[String, Any] = mutable.Map()
    private val toSend: mutable.Map[String, Array[Byte]] = mutable.Map()

    case class InvocationCoordinate(key: String, invocationCount: Int):
      override def toString: String = s"$key.$invocationCount"

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
    override def neighborsValuesAt[V: Decoder as decoder](path: String): Map[Int, V] =
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
    override protected[locicope] val localPeerRepr: PeerRepr = peerRepr

    override def repeat[Value](using vm: VM)(initial: Value)(f: Value => Value): Value =
      vm.align("repeat"): () =>
        val result = f(vm.stateAt(vm.currentPath).getOrElse(initial))
        vm.setStateAt(result)
        result

    override def neighbors[Value: Codec](using vm: VM)(value: Value): Field[Value] =
      vm.align("neighbors"): () =>
        vm.setValueAt(value)
        Field(value, vm.neighborsValuesAt[Value](vm.currentPath))

    override def branch[Value](using vm: VM)(condition: Boolean)(ifTrue: => Value)(ifFalse: => Value): Value =
      vm.align("branch"): () =>
        if condition then ifTrue else ifFalse

    override def mux[Value](using VM)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value =
      if condition then ifTrue else ifFalse

  trait Effect:
    protected[locicope] val localPeerRepr: PeerRepr

    case class Field[V](local: V, overrides: Map[Int, V]):
      def sum(using num: Numeric[V]): V =
        import num.*
        overrides.values.foldLeft(local)(plus)

    trait VM:
      def currentPath: String
      def align[V](slot: String)(body: () => V): V
      def stateAt[V](path: String): Option[V]
      def setStateAt[V](value: V): Unit
      def neighborsValuesAt[V: Decoder](path: String): Map[Int, V]
      def setValueAt[V: Encoder](value: V): Unit
      def createExport: Map[String, Array[Byte]]
      def createState: Map[String, Any]

    def repeat[Value](using VM)(initial: Value)(f: Value => Value): Value
    def neighbors[Value: Codec](using VM)(value: Value): Field[Value]
    def branch[Value](using VM)(condition: Boolean)(ifTrue: => Value)(ifFalse: => Value): Value
    def mux[Value](using VM)(condition: Boolean)(ifTrue: Value)(ifFalse: Value): Value
end Collective
