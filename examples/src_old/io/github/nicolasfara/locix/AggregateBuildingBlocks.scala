package io.github.nicolasfara.locix

import Collective.*
import io.github.nicolasfara.locix.FieldOps.*
import io.github.nicolasfara.locix.network.Network.Network

object AggregateBuildingBlocks:

  trait DistanceSensor:
    def nbrRange(using coll: Collective, vm: coll.effect.VM^): vm.Field[Double]

  def G[V](using coll: Collective, vm: coll.effect.VM^)(source: Boolean, initial: V, acc: V => V, metric: () => vm.Field[Double]) =
    repeat((Double.MaxValue, initial)): dv =>
      mux(source) {
        (0.0, initial)
      } {
        val (d, v) = neighbors((dv._1, dv._2)).combine(metric()) { case (tuple, metric) =>
          (tuple._1 + metric, tuple._2)
        }.overrides.values.minByOption(_._1).getOrElse((Double.MaxValue, dv._2))
        // println(s"${d} and $v")
        (d, acc(v))
        // val d = neighbors(dv._1) + metric()
        // val v = acc(dv._2)
        // (d.minPlus(Double.MaxValue), v)
      }
    ._2

  def distanceTo(using coll: Collective, vm: coll.effect.VM^, sensor: DistanceSensor)(source: Boolean) =
    G[Double](source, 0.0, _ + sensor.nbrRange.minPlus(Double.MaxValue), () => sensor.nbrRange)

  def broadcast[V](using coll: Collective, vm: coll.effect.VM^, sensor: DistanceSensor)(source: Boolean, value: V) =
    G[V](source, value, identity, () => sensor.nbrRange)

  def distanceBetween(using coll: Collective, vm: coll.effect.VM^, sensor: DistanceSensor)(source: Boolean, target: Boolean) =
    broadcast[Double](source, distanceTo(target))