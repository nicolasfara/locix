/*
 * Focused excerpt from Scafi demos/src/main/scala/sims/CollectionDemo.scala
 */

package sims

import it.unibo.scafi.incarnations.BasicSimulationIncarnation.*

class Collection extends AggregateProgram with SensorDefinitions with BlockC with BlockG:
  def summarize(sink: Boolean, acc: (Double, Double) => Double, local: Double, Null: Double): Double =
    broadcast(sink, C(distanceTo(sink), acc, local, Null))

  override def main(): Double =
    summarize(sense1, _ + _, if sense2 then 1.0 else 0.0, 0.0)
