/*
 * Focused excerpt from Scafi demos/src/main/scala/sims/ExplicitFields.scala
 */

package sims

import it.unibo.scafi.incarnations.BasicSimulationIncarnation.{ AggregateProgram, ExplicitFields }

class GradientWithExplicitFields extends AggregateProgram with SensorDefinitions with ExplicitFields:
  override def main(): Double = gradient(sense1)

  def gradient(src: Boolean): Double =
    rep(Double.PositiveInfinity): distance =>
      mux(src) {
        0.0
      } {
        (fnbr(distance) + fsns(nbrRange)).minHoodPlus
      }
