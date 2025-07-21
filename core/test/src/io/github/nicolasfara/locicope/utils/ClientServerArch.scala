package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.{ Multiple, Single }

object ClientServerArch:
  type Client <: Peer { type Tie <: Single[Server] }
  type Server <: Peer { type Tie <: Multiple[Client] }
