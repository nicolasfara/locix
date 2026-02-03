package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.Collective.Collective

object FieldOps:
  extension [V: Numeric as numeric](using coll: Collective, vm: coll.effect.VM^)(field: vm.Field[V])
    def sum: V = field.overrides.values.fold(field.local)(numeric.plus)
    def min: V = field.overrides.values.fold(field.local)(numeric.min)
    def minPlus(default: V): V = field.overrides.values.fold(default)(numeric.min)
    def max: V = field.overrides.values.fold(field.local)(numeric.max)
    def +(other: vm.Field[V]): vm.Field[V] = 
      val summedOverrides = (field.overrides.keySet ++ other.overrides.keySet).map { k =>
        val v1 = field.overrides.getOrElse(k, field.local)
        val v2 = other.overrides.getOrElse(k, other.local)
        (k, numeric.plus(v1, v2))
      }.toMap
      vm.Field[V](numeric.plus(field.local, other.local), summedOverrides)
  extension [V](using coll: Collective, vm: coll.effect.VM^)(field: vm.Field[V])
    def map[B](f: V => B): vm.Field[B] = 
      val mappedOverrides = field.overrides.map { case (k, v) => (k, f(v)) }
      vm.Field[B](f(field.local), mappedOverrides)
    def combine[A, B](other: vm.Field[A])(f: (V, A) => B): vm.Field[B] =
      val combinedOverrides = (field.overrides.keySet ++ other.overrides.keySet).map { k =>
        val v1 = field.overrides.getOrElse(k, field.local)
        val v2 = other.overrides.getOrElse(k, other.local)
        (k, f(v1, v2))
      }.toMap
      vm.Field[B](f(field.local, other.local), combinedOverrides)