error id: file://<WORKSPACE>/core/src/io/github/nicolasfara/locicope/Multitier.scala:
file://<WORKSPACE>/core/src/io/github/nicolasfara/locicope/Multitier.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -Codec#
	 -scala/Predef.Codec#
offset: 629
uri: file://<WORKSPACE>/core/src/io/github/nicolasfara/locicope/Multitier.scala
text:
```scala
package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.PlacementType.on
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.PlacementType.PeerScope
import ox.flow.Flow

object Multitier:
  type Multitier = Locicope[Multitier.Effect]

  class MultitierPeerScope[P <: Peer](val peerRepr: PeerRepr) extends PlacementType.PeerScope[P]

  trait Effect:
    trait PlacedFunction[-In <: Product: Cod@@ec, Out: Encoder, P[_, _ <: Peer]: Placeable, Local <: Peer]:
      val localPeerRepr: PeerRepr
      val resourceReference: ResourceReference
      override def toString: String = s"λ@${localPeerRepr.baseTypeRepr}"
    def apply(inputs: In): P[Out, Local]
    def placed[V: Encoder, P <: Peer](body: PeerScope[P] ?=> V)(using Net): V on P
    def placedFlow[V: Encoder, P <: Peer](body: PeerScope[P] ?=> Flow[V])(using Net): Flow[V] on P

```


#### Short summary: 

empty definition using pc, found symbol in pc: 