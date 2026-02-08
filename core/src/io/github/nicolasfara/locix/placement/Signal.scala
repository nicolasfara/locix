package io.github.nicolasfara.locix.placement

import scala.caps.Control
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Identifier


trait Signal[V] extends Control:
  def id: Identifier
  def notify(using Network)(value: V): Unit
  def close(using Network): Unit

object Signal:
  def apply[P <: Peer, V](using n: Network, p: PlacementType, s: p.PeerScope[P]): Signal[V] = new Signal[V] {
    override def id: Identifier = s.id
    override def notify(using Network)(value: V): Unit = ???
    override def close(using Network): Unit = ???
  }