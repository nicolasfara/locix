package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Choreography.Choreography
import io.github.nicolasfara.locicope.Choreography.comm
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.utils.TestCodec.given
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue.{ on, take }
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.utils.TwoPeersArch.*
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.stub.IntNetwork
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.serialization.Encoder
import ox.flow.Flow
import io.github.nicolasfara.stub.NoOpIntNetwork

class ChoreographyTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  // private val netEffect = stub[IntNetwork]
  // given net: Locicope[Network.Effect](netEffect)
  type Id[V] = V

  before:
    resetStubs()

  "The `Choreography` capability" should "allow explicit communication between two peers" in:
    ()
    // (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("peerA"))
    // // (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())
    // (netEffect
    //   .receive[Id, Int, PeerA, PeerB](_: String, _: Reference)(using _: Decoder[Int]))
    //   .returns:
    //     case ("peerA", _, _) => Right(42)
    //     case _ => fail("Unexpected receive")

    // val result = PlacedValue.run[PeerB]:
    //   Choreography.run[PeerB]:
    //     val valueOnPeerA: Int on PeerA = on[PeerA](42)
    //     val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
    //     take(receivedValue)

    // result shouldBe 42
    // (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
    // // (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Register the flow on the network
    // (netEffect.receive[Id, Int, PeerA, PeerB](_: String, _: Reference)(using _: Decoder[Int])).times shouldBe 1 // Receive the value from peerA
  it should "send the value when the placed value is local" in:
    given network: Locicope[NoOpIntNetwork](NoOpIntNetwork())

    val result = PlacedValue.run[PeerA]:
      Choreography.run[PeerA]:
        val valueOnPeerA: Int on PeerA = on[PeerA](42)
        val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
    network.effect.setValues should contain(42)
  it should "receive the value when the placed value is remote" in:
    given network: Locicope[NoOpIntNetwork](NoOpIntNetwork())

    val result = PlacedValue.run[PeerB]:
      Choreography.run[PeerB]:
        val valueOnPeerA: Int on PeerA = on[PeerA](42)
        val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
        receivedValue.take shouldBe 42
        ()
end ChoreographyTest
