package io.github.locix

import io.github.locix.CollectiveBuildingBlocks.DistanceSensor
import io.github.locix.Collective.nbr

object NbrSensor:
  def sensor(localAddress: String, positions: Map[String, (Double, Double)])(using Collective): DistanceSensor = new DistanceSensor:
    def nbrRange(using Collective, VM): Field[Double] =
      val localPos = positions(localAddress)
      nbr(localPos).map { position =>
        math.sqrt(
          math.pow(localPos._1 - position._1, 2) +
            math.pow(localPos._2 - position._2, 2),
        )
      }