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

object GradientWithExplicitFields:
  type Node <: { type Tie <: Multiple[Node] }

  private def gradientApp(using Network, Placement, Collective): Unit =
    given DistanceSensor = distanceSensor(Line4)
    val localPeer = peerAddress.asInstanceOf[String]
    val gradient = Collective[Node](Line4.round):
      val isSource = localPeer == "node-1"
      rep(Double.PositiveInfinity): distance =>
        mux(isSource) {
          0.0
        } {
          val inRangeMetrics = nbrRange.map: step =>
            if step <= Line4.communicationRadius then step else Double.PositiveInfinity
          nbr(distance)
            .combine(inRangeMetrics)(_ + _)
            .withoutSelf
            .values
            .minOption
            .getOrElse(Double.PositiveInfinity)
        }

    on[Node]:
      val value = awaitLatest(take(gradient), convergenceWindow(Line4))
      println(f"[${peerAddress}] $value%.1f")

  def main(args: Array[String]): Unit =
    runCollectiveExample[Node](gradientApp)
end GradientWithExplicitFields
