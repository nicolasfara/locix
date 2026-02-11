package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.placement.Signal
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

sealed trait VM:
  opaque type ValueTree = Map[String, Any]
  // opaque type Path = String

  private var context: Map[String, Map[String, Any]] = Map.empty
  private val messages = mutable.Map.empty[String, Any]
  private val state = mutable.Map.empty[String, Any]

  def align(token: String): Unit = ???
  def dealign(): Unit = ???

  def outbound: ValueTree = messages.toMap

  def prepareInbound(neighborsValueTrees: Map[String, ValueTree]): Unit =
    context = Map.empty
    neighborsValueTrees.foreach { case (neighborId, valueTree) =>
      valueTree.foreach { case (path, value) =>
        val pathMap = context.getOrElse(path, Map.empty[String, Any])
        context = context + (path -> (pathMap + (neighborId -> value)))
      }
    }

trait Collective extends Multiparty:
  type Field[+V]

  def rep[V](using VM)(initial: V)(evolution: V -> V): V
  def nbr[V](using VM)(value: V): Field[V]
  def branch[V](using VM)(condition: Boolean)(trueBranch: -> V)(falseBranch: -> V): V
  def mux[V](using VM)(condition: Boolean)(trueValue: V)(falseValue: V): V

object Collective:
  def apply[P <: TiedManyWith[P]: PeerTag](using n: Network, p: Placement, ec: ExecutionContext)[V](round: FiniteDuration)(program: VM^ ?-> V): Signal[V] on P = on[P]:
    given vm: (VM^) = new VM {}
    given Raise[NetworkError] = Raise.rethrowError
    val scopeId = summon[PeerScope[P]].id
    val key: Identifier = scopeId.copy(namespace = Some("collective"), metadata = Map("valuetree" -> "true"))
    // signalling: signal =>
    //   while true do
    //     val neighbors = n.reachablePeersOf[P]
    //     val neighborValues = n.pullFromAll[P, P, vm.ValueTree](neighbors, key).map { case (k, v) => k.toString() -> v }.toMap
    //     vm.prepareInbound(neighborValues)
    //     val result = program(using vm)
    //     val outboundValueTree = vm.outbound
    //     neighbors.foreach(neighbor => n.push[P, P, vm.ValueTree](neighbor, key, outboundValueTree))
    //     signal.emit(result)
    //     Thread.sleep(round.toMillis)
    ???
