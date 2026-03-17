package io.github.locix

import scala.concurrent.ExecutionContext.Implicits.global

import io.github.locix.Collective
import io.github.locix.Collective.*
import io.github.locix.CollectiveBuildingBlocks.DistanceSensor
import io.github.locix.CollectiveExampleSupport.*
import io.github.locix.network.Network
import io.github.locix.network.Network.peerAddress
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.placement.PeerScope.take
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType.on

object Collection:
  type Node <: { type Tie <: Multiple[Node] }

  private def isContributor(peerId: String): Boolean =
    peerId == "node-2" || peerId == "node-4"

  private def collectionApp(using Network, Placement, Collective): Unit =
    given DistanceSensor = distanceSensor(Line4)
    val localPeer = peerAddress.asInstanceOf[String]
    val collection = Collective[Node](Line4.round):
      val isSource = localPeer == "node-1"
      val localContribution = if isContributor(localPeer) then 1.0 else 0.0
      val potential = distanceToWithinRadius(isSource)
      val collected = collectAlongPotential(potential, localContribution, 0.0)(_ + _)
      broadcastWithinRadius(isSource, collected)

    on[Node]:
      val value = awaitLatest(take(collection), convergenceWindow(Line4, propagationPhases = 6))
      println(f"[${peerAddress}] $value%.1f")

  def main(args: Array[String]): Unit =
    runCollectiveExample[Node](collectionApp)
end Collection
