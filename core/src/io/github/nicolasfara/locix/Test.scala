package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PeerScope.*
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.Choreography
import io.github.nicolasfara.locix.Choreography.*
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.signal.Signal
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.nicolasfara.locix.signal.Signal.signalBuilder
import io.github.nicolasfara.locix.placement.Placement

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
