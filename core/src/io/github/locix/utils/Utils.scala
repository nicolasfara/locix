package io.github.locix.utils

import io.github.locix.peers.Peers.PeerTag
import io.github.locix.peers.Peers.Peer

object Utils:
  def selectWithDefault[V, L <: Peer, P1 <: Peer, P2 <: Peer](
    local: PeerTag[L],
    p1: PeerTag[P1],
    p2: PeerTag[P2],
  )[C^](onLocal: => V^{C}, onRemote: => V^{C}, default: => V^{C}): V^{C} =
    if local == p1 then onLocal
    else if local == p2 then onRemote
    else default

  def select[V, L <: Peer, P1 <: Peer](local: PeerTag[L], p1: PeerTag[P1])(onLocal: => V, onRemote: => V): V =
    if local == p1 then onLocal else onRemote