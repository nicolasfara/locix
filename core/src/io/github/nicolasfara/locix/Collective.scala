package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.signal.Signal
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.placement.Placement
import scala.concurrent.duration.FiniteDuration
import io.github.nicolasfara.locix.network.Network
import scala.concurrent.ExecutionContext
import io.github.nicolasfara.locix.peers.Peers.TiedManyWith
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.Identifier
import io.github.nicolasfara.locix.placement.PeerScope
import scala.collection.mutable
import scala.caps.Mutable
import io.github.nicolasfara.locix.signal.Signal.signalBuilder
import scala.caps.SharedCapability

sealed trait VM:
  opaque type ValueTree = Map[String, Any]
  type Path = String
  type DeviceId

  private var context: Map[Path, Map[DeviceId, Any]] = Map.empty
  private val messages = mutable.Map.empty[Path, Any]
  private val state = mutable.Map.empty[Path, Any]

  private val stack = mutable.Stack.empty[InvocationCoordinate]
  private val trace = mutable.Map.empty[Path, Int]
  private case class InvocationCoordinate(key: String, invocationCount: Int):
    override def toString(): String = s"$key#$invocationCount"

  def deviceId: DeviceId

  def align(token: String): Unit =
    val invocationCount = trace.get(currentPath).map(_ + 1).getOrElse(0)
    stack.push(InvocationCoordinate(token, invocationCount))
  def dealign(): Unit =
    if stack.nonEmpty then
      val coord = stack.pop()
      trace.update(currentPath, coord.invocationCount)
    else throw new RuntimeException("Dealign called without a matching align")
  def currentPath: Path = stack.reverse.mkString("/")

  def setStateAt[V](path: Path, value: V): Unit = state.update(path, value)
  def getStateAt[V](path: Path): Option[V] = state.get(path).map(_.asInstanceOf[V])
  def getState: Map[Path, Any] = state.toMap

  def neighborValuesAt[V](path: Path): Map[DeviceId, V] = context.get(path).map(_.view.mapValues(_.asInstanceOf[V]).toMap).getOrElse(Map.empty)
  def setValueAt[V](path: Path, value: V): Unit = messages.update(path, value)

  def outbound: ValueTree = messages.toMap
  def prepareInbound(neighborsValueTrees: Map[DeviceId, ValueTree], prevState: Map[Path, Any]): Unit =
    resetVm()
    state ++= prevState
    context = Map.empty
    neighborsValueTrees.foreach { case (neighborId, valueTree) =>
      valueTree.foreach { case (path, value) =>
        val pathMap = context.getOrElse(path, Map.empty[DeviceId, Any])
        context = context + (path -> (pathMap + (neighborId -> value)))
      }
    }
  private def resetVm(): Unit =
    context = Map.empty
    messages.clear()
    state.clear()
    stack.clear()
    trace.clear()

trait Field[+V] extends SharedCapability:
  def withoutSelf: Iterable[V]
  def map[U](f: V -> U): Field[U]
  def localValue: V

object Field:
   extension [N: Numeric](field: Field[N])
    def sum: N = field.withoutSelf.fold(field.localValue)(Numeric[N].plus)
    def sumWithoutSelf(default: N): N = field.withoutSelf.fold(default)(Numeric[N].plus)
    def max: N = field.maxWithoutSelf(field.localValue)
    def maxWithoutSelf(default: N): N = field.withoutSelf.fold(default)(Numeric[N].max)
    def min: N = field.minWithoutSelf(field.localValue)
    def minWithoutSelf(default: N): N = field.withoutSelf.fold(default)(Numeric[N].min)

trait Collective extends Multiparty:
  def rep[V](using VM)(initial: V)(evolution: V -> V): V
  def nbr[V](using VM)(value: V): Field[V]
  def branch[V](using VM)(condition: Boolean)(trueBranch: -> V)(falseBranch: -> V): V
  def mux[V](using VM)(condition: Boolean)(trueValue: V)(falseValue: V): V

object Collective:
  def rep[V](using c: Collective, vm: VM)(initial: V)(evolution: V -> V): V = c.rep(initial)(evolution)
  def nbr[V](using c: Collective, vm: VM)(value: V): Field[V] = c.nbr(value)
  def branch[V](using c: Collective, vm: VM)(condition: Boolean)(trueBranch: -> V)(falseBranch: -> V): V = c.branch(condition)(trueBranch)(falseBranch)
  def mux[V](using c: Collective, vm: VM)(condition: Boolean)(trueValue: V)(falseValue: V): V = c.mux(condition)(trueValue)(falseValue)

  def apply[P <: TiedManyWith[P]: PeerTag](using
    n: Network,
    p: Placement,
    c: Collective,
    ec: ExecutionContext
  )[V](round: FiniteDuration)(program: VM^ ?->{c} V): Signal[V] on P = on[P]:
    given vm: (VM { type DeviceId = n.PeerAddress }) = new VM:
      type DeviceId = n.PeerAddress
      override def deviceId = n.peerAddress
    given Raise[NetworkError] = Raise.rethrowError
    val scopeId = summon[PeerScope[P]].id
    val key: Identifier = scopeId.copy(namespace = Some("collective"), metadata = Map("valuetree" -> "true"))
    signalBuilder: signal =>
      while true do
        val neighbors = n.reachablePeersOf[P]
        val neighborValues= neighbors
          .map(neighbor => neighbor -> n.retrieveNow[P, vm.ValueTree](neighbor, key))
          .collect { case (neighbor, Some(valueTree)) => neighbor -> valueTree }
          .toMap
        vm.prepareInbound(neighborValues, vm.getState)
        val result = program(using vm)
        val outboundValueTree = vm.outbound
        neighbors.foreach(neighbor => n.push[P, P, vm.ValueTree](neighbor, key, outboundValueTree))
        signal.emit(result)
        Thread.sleep(round.toMillis)
