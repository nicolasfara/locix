package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Collective.Collective

object FieldOps:
  extension [V: Numeric as numeric](using coll: Collective, vm: coll.effect.VM)(field: vm.Field[V])
    def sum: V = field.overrides.values.fold(field.local)(numeric.plus)
    def min: V = field.overrides.values.fold(field.local)(numeric.min)
    def max: V = field.overrides.values.fold(field.local)(numeric.max)
