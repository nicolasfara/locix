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
