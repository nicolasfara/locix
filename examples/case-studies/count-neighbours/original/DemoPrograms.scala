/*
 * Focused excerpt from Scafi demos/src/main/scala/sims/DemoPrograms.scala
 */

package sims

import it.unibo.scafi.incarnations.BasicSimulationIncarnation.*

class CountNeighbours extends AggregateProgram:
  override def main(): Int =
    foldhood(0)(_ + _) { nbr { 1 } }
