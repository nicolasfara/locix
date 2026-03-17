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

object CollectionIds:
  type Node <: { type Tie <: Multiple[Node] }

  private def isContributor(peerId: String): Boolean =
    peerId == "node-2" || peerId == "node-4"

  private def formatIds(ids: Set[Int]): String =
    ids.toList.sorted.mkString("{", ",", "}")

  private def collectionIdsApp(using Network, Placement, Collective): Unit =
    given DistanceSensor = distanceSensor(Line4)
    val localPeer = peerAddress.asInstanceOf[String]
    val collection = Collective[Node](Line4.round):
      val isSource = localPeer == "node-1"
      val localIds =
        if isContributor(localPeer) then Set(peerOrdinal(localPeer))
        else Set.empty[Int]
      val potential = distanceToWithinRadius(isSource)
      collectAlongPotential(potential, localIds, Set.empty[Int])(_ union _)

    on[Node]:
      val value = awaitLatest(take(collection), convergenceWindow(Line4, propagationPhases = 2))
      println(s"[${peerAddress}] ${formatIds(value)}")

  def main(args: Array[String]): Unit =
    runCollectiveExample[Node](collectionIdsApp)
end CollectionIds
