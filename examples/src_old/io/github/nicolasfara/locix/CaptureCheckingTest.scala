package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.on
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.Multitier.*

object CaptureCheckingTest:
  type Client <: { type Tie <: Quantifier.Single[Server] }
  type Server <: { type Tie <: Quantifier.Multiple[Client] }

  def foo(ps: Int on Server)(using Network, PlacedValue, Multitier): (Int => Int) on Client = on[Client]: i =>
    i + 42 // asLocal(ps)
