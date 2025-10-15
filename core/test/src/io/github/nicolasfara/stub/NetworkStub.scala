package io.github.nicolasfara.stub

import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer

type Id = [A] =>> A

trait IntNetwork extends Network.Effect:
  type Id = Int
  type NetworkError = Throwable
  type Address[P <: Peer] = String
