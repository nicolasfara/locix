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

object Test:
  type Pinger <: { type Tie <: Single[Ponger] }
  type Ponger <: { type Tie <: Single[Pinger] }

  def bar(using Network, PlacementType^, Multitier) = Multitier:
    val p = on[Pinger] { "Hello from the pinger!" }
    on[Ponger] { asLocal(p).length }

  def b(using Network, PlacementType^, Choreography) = on[Pinger] { "Hello from the pinger!" }

  def foo(using n: Network, p: PlacementType^, c: Choreography, m:Multitier)(value: Int on Ponger) =
    val a = Choreography:
      val onPinger = on[Pinger]:
        signalBuilder: em =>
          Thread.sleep(1000)
          em.emit("Hello from the pinger!")
      val messageOnPonger = comm[Pinger, Ponger](onPinger)
      on[Ponger]:
        take(messageOnPonger).subscribe(println)
        // comm[Ponger, Pinger](messageOnPonger)
        // val sig = take(messageOnPonger)
        // sig.subscribe(println)
        // on[Pinger] { "ds" }
        // b
      on[Pinger] { "ds" }
    Multitier:
      // comm[Ponger, Pinger](???)
      on[Ponger] { asLocal(a) }
    // val b = Multitier { summon[Scope[MultitierScope]] }
    // b

  def baz(using Network, PlacementType^, Choreography, Multitier) =
    // val r = bar
    // foo(r)
    ???
