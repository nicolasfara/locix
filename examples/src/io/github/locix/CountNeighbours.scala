package io.github.locix

import scala.concurrent.ExecutionContext.Implicits.global

import io.github.locix.Collective
import io.github.locix.Collective.*
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor.nbrRange
import io.github.locix.CollectiveExampleSupport.*
import io.github.locix.network.Network
import io.github.locix.network.Network.peerAddress
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType.on

object CountNeighbours:
  type Node <: { type Tie <: Multiple[Node] }

  private def countNeighboursApp(using Network, Placement, Collective): Unit =
    given DistanceSensor = distanceSensor(Line4)
    val neighbourCount = Collective[Node](Line4.round):
      nbr(1)
        .combine(nbrRange): (one, distance) =>
          if distance <= Line4.communicationRadius then one else 0
        .sum

    on[Node]:
      val value = awaitLatest(take(neighbourCount), Line4.observationWindow)
      println(s"[${peerAddress}] $value")

  def main(args: Array[String]): Unit =
    runCollectiveExample[Node](countNeighboursApp)
end CountNeighbours
