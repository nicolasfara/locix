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

  def bar(using Network, PlacementType^, Multitier) = Multitier:
    val p = on[Pinger] { "Hello from the pinger!" }
    on[Ponger] { asLocal(p).length }

  def b(using Network, PlacementType^, Choreography) = on[Pinger] { "Hello from the pinger!" }

  def foo(using Network, PlacementType^, Choreography, Multitier)(value: Int on Ponger) =
    val a = Choreography:
      val onPinger = on[Pinger] { "Hello from the pinger!" }
      val messageOnPonger = comm[Pinger, Ponger](onPinger)
      on[Ponger]:
        // 10
        // comm[Ponger, Pinger](messageOnPonger)
        take(messageOnPonger)
        // on[Pinger] { "ds" }
        // b
      on[Pinger] { "ds" }
    Multitier:
      // comm[Ponger, Pinger](???)
      on[Pinger] { "ds" }

  def baz(using Network, PlacementType^, Choreography, Multitier) =
    val r = bar
    foo(r)
