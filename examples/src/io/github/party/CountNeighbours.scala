package io.github.party

import scala.concurrent.ExecutionContext.Implicits.global

import io.github.party.Collective
import io.github.party.Collective.*
import io.github.party.CollectiveBuildingBlocks.DistanceSensor
import io.github.party.CollectiveBuildingBlocks.DistanceSensor.nbrRange
import io.github.party.CollectiveExampleSupport.*
import io.github.party.network.Network
import io.github.party.network.Network.peerAddress
import io.github.party.peers.Peers.Cardinality.*
import io.github.party.placement.PeerScope.take
import io.github.party.placement.Placement
import io.github.party.placement.PlacementType.on

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
