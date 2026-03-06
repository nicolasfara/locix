/*
 * Copyright (C) 2016-2019, Roberto Casadei, Mirko Viroli, and contributors.
 * See the LICENSE file distributed with this work for additional information regarding copyright ownership.
*/

package sims

import java.time.Instant
import java.time.temporal.ChronoUnit

import it.unibo.scafi.incarnations.BasicSimulationIncarnation._
import it.unibo.scafi.lib.LibExtTypeClasses
import it.unibo.scafi.simulation.frontend.{Launcher, Settings}
import it.unibo.scafi.space.Point3D
import sims.DoubleUtils.Precision

import scala.concurrent.duration.FiniteDuration

object GradientsDemo extends Launcher {
  // Configuring simulation
  Settings.Sim_ProgramClass = "sims.GradientWithObstacle" // starting class, via Reflection
  Settings.ShowConfigPanel = false // show a configuration panel at startup
  Settings.Sim_NbrRadius = 0.15 // neighbourhood radius
  Settings.Sim_NumNodes = 100 // number of nodes
  Settings.ConfigurationSeed = 0
  launch()
}

class GradientWithObstacle extends AggregateProgram with SensorDefinitions with GradientAlgorithms {
  def main(): Double = g2(sense1, sense2)

  def g2(isSrc: Boolean, isObstacle: Boolean): Double = branch(isObstacle){
    Double.PositiveInfinity
  }{
    classic(isSrc)
  }
}

/*#################################
  ############ CLASSIC ############
  #################################*/

def classic(source: Boolean): Double = rep(Double.PositiveInfinity){ distance =>
  mux(source){ 0.0 }{
    // NB: must be minHoodPlus (i.e., not the minHood which includes the device itself)
    //     otherwise a source which stops being a source will continue to count as 0 because of self-messages.
    minHoodPlus(nbr{distance} + nbrRange)
  }
}
