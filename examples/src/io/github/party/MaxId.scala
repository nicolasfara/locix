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

object MaxId:
  type Node <: { type Tie <: Multiple[Node] }

  private def maxIdApp(using Network, Placement, Collective): Unit =
    given DistanceSensor = distanceSensor(Line4)
    val localId = peerOrdinal(peerAddress.asInstanceOf[String])
    val maxVisibleId = Collective[Node](Line4.round):
      val visibleIds = nbr(localId).combine(nbrRange): (neighborId, distance) =>
        if distance <= Line4.communicationRadius then neighborId else Int.MinValue
      (localId, visibleIds.max)

    on[Node]:
      val value = awaitLatest(take(maxVisibleId), Line4.observationWindow)
      println(s"[${peerAddress}] $value")

  def main(args: Array[String]): Unit =
    runCollectiveExample[Node](maxIdApp)
end MaxId
