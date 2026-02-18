package io.github.nicolasfara.locix.handlers

import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.Collective
import io.github.nicolasfara.locix.VM
import io.github.nicolasfara.locix.Field
import io.github.nicolasfara.locix.network.Network

private[locix] case class FieldImpl[+V, DeviceId](localId: DeviceId, override val localValue: V, values: Map[DeviceId, V]) extends Field[V]:
  type Id = DeviceId
  def map[U](f: V -> U): io.github.nicolasfara.locix.Field[U] = FieldImpl(localId, f(localValue), values.view.mapValues(f).toMap)

  def withoutSelf: Map[Id, V] = values.filterNot(_._1 == localId)

  def combine[A, B](that: Field[A])(f: (V, A) -> B): Field[B] =
    val local = f(this.localValue, that.localValue)
    val combinedValues = this.values.flatMap: 
      case (id: that.Id @unchecked, v) => that.withoutSelf.get(id).map(a => id -> f(v, a))
      case _ => None
    FieldImpl(localId, local, combinedValues.toMap)

private final class CollectiveHandlerImpl[P <: Peer: PeerTag, Id] extends Collective:
  def rep[V](using vm: VM)(initial: V)(evolution: V -> V): V =
    vm.align("rep")
    val result = evolution(vm.getStateAt(vm.currentPath).getOrElse(initial))
    vm.setStateAt(vm.currentPath, result)
    vm.dealign()
    result

  def nbr[V](using vm: VM)(value: V): Field[V] =
    vm.align("nbr")
    vm.setValueAt(vm.currentPath, value)
    val neighbors = vm.neighborValuesAt[V](vm.currentPath)
    vm.dealign()
    FieldImpl(vm.deviceId, value, neighbors)

  def branch[V](using vm: VM)(condition: Boolean)(trueBranch: -> V)(falseBranch: -> V): V =
    vm.align(s"branch[$condition]")
    val result = if condition then trueBranch else falseBranch
    vm.dealign()
    result

  def mux[V](using vm: VM)(condition: Boolean)(trueValue: V)(falseValue: V): V =
    if condition then trueValue else falseValue

object CollectiveHandler:
  def handle[P <: Peer: PeerTag, DeviceId, V](using n: Network): Collective = new CollectiveHandlerImpl[P, n.PeerAddress]
