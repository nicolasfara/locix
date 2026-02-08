package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.Choreography
import io.github.nicolasfara.locix.Choreography.*
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.network.Network

object Test:
  type Pinger <: { type Tie <: One[Ponger] }
  type Ponger <: { type Tie <: One[Pinger] }

  def bar(using Network^, PlacementType, Multitier): Int on Ponger = Multitier:
    val p = on[Pinger] { "Hello from the pinger!" }
    on[Ponger] { asLocal(p).length }

  def b(using n: Network, p: PlacementType, c: Choreography, g: p.OnGuard^) = on[Pinger] { "Hello from the pinger!" }

  def foo(using n: Network, p: PlacementType, c: Choreography, m: Multitier)(value: Int on Ponger) = Choreography:
    val onPinger = on[Pinger] { "Hello from the pinger!" }
    val messageOnPonger = comm[Pinger, Ponger](onPinger)
    val a = on[Ponger]:
      take(messageOnPonger)
      // on[Ponger] { "ds" }
      ()
    // Multitier.apply:
    //   on[Ponger] { asLocal(onPinger) }
    ???

  def baz(using Network, PlacementType, Choreography, Multitier) =
    val r = bar
    foo(r)
