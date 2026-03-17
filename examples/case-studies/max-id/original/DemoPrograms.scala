/*
 * Focused excerpt from Scafi demos/src/main/scala/sims/DemoPrograms.scala
 */

package sims

import it.unibo.scafi.incarnations.BasicSimulationIncarnation.*

class MaxId extends AggregateProgram:
  override def main(): (ID, Int) =
    val maxId = foldhood(Int.MinValue)(Math.max(_, _)) { nbr(mid()) }
    (mid(), maxId)
