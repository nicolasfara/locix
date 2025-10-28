package io.github.nicolasfara.locicope.utils

import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.placement.Peers.Quantifier.{ Multiple, Single }

object ClientServerArch:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Multiple[Client] }

object CpsArch:
  type Smartphone <: { type Tie <: Multiple[Smartphone] & Single[Server] }
  type Server <: { type Tie <: Multiple[Smartphone] }

object TwoPeersArch:
  type PeerA <: { type Tie <: Single[PeerB] }
  type PeerB <: { type Tie <: Single[PeerA] }
