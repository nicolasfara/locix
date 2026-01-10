package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locix.placement.Peers.Quantifier
import io.github.nicolasfara.locix.placement.PlacedValue.on
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.placement.PlacementType.PeerScope

object HigherOrderCapability:
  type Client <: { type Tie <: Quantifier.Single[Server] }
  type Server <: { type Tie <: Quantifier.Single[Client] }
  type Cache <: { type Tie <: Quantifier.Single[Server] }

  // def foo(pl: Int on Server)(using net: Network, pv: PlacedValue, mt: Multitier) = on[Client]:
  //   (i: Int) => asLocal(pl)
