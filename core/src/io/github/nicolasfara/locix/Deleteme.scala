package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier
import io.github.nicolasfara.locix.placement.Peers.Peer
import io.github.nicolasfara.locix.placement.Peers.PeerRepr

@main def main(): Unit =
  type Foo <: { type Tie <: Quantifier.Multiple[Foo] }

  def f[P <: Peer: PeerRepr] = 
    val pr = summon[PeerRepr[P]]
    println(s"PeerRepr: baseTypeRepr = ${pr.baseTypeRepr}")

  def p[P <: Peer: PeerRepr] = f[P]

  p[Foo]
