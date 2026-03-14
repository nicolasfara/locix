package io.github.locix

import scala.concurrent.ExecutionContext.Implicits.global

import io.github.locix.Choreography
import io.github.locix.Choreography.*
import io.github.locix.Multitier.*
import io.github.locix.network.Network
import io.github.locix.peers.Peers.Cardinality.*
import io.github.locix.placement.PeerScope.*
import io.github.locix.placement.Placement
import io.github.locix.placement.PlacementType
import io.github.locix.placement.PlacementType.*
import io.github.locix.signal.Signal
import io.github.locix.signal.Signal.signalBuilder

object Test:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def b(using Network, Placement) = on[Pinger] { "Hello from the pinger!" }

  def nesting(using Network, Placement) = on[Pinger]:
    // on[Ponger] { "Hello from the ponger!" }
    // b
    42

  // def mixParadigms(using Network, PlacementType^, Choreography, Multitier) =
  //   val a = Choreography:
  //     val onPinger = on[Pinger] { "Hello from the pinger!" }
  //     val messageOnPonger = comm[Pinger, Ponger](onPinger)
  //     val onPonger = on[Ponger] { take(messageOnPonger) }
  //     val res: String on Pinger = on[Pinger] { asLocal(onPonger) }

  // def higherOrderCapability(using Network, Placement, Multitier) = Multitier:
  //   val onPonger = on[Ponger] { "hello" }
  //   val res = on[Pinger] { (i: Int) => asLocal(onPonger) }
end Test
