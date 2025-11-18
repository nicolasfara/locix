package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.Choreography.comm
import io.github.nicolasfara.locix.network.Network
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locix.utils.TestCodec.given
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.{ on, take }
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.utils.TwoPeersArch.*
import io.github.nicolasfara.locix.placement.Peers.PeerRepr
import io.github.nicolasfara.stub.IntNetwork
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.serialization.Decoder
import io.github.nicolasfara.locix.serialization.Encoder
import io.github.nicolasfara.stub.NoOpIntNetwork
import io.github.nicolasfara.locix.{ Choreography, Locix }

class ChoreographyTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private val netEffect = stub[IntNetwork]
  given net: Locix[Network.Effect](netEffect)
  type Id[V] = V

  before:
    resetStubs()

  "The `Choreography` capability" should "allow explicit communication between two peers" in:
    (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("peerA"))
    (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).returnsWith(Right(()))
    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())
    (netEffect
      .receive[PeerA, PeerB, Id, Int](_: String, _: Reference)(using _: Decoder[Int]))
      .returns:
        case ("peerA", _, _) => Right(42)
        case _ => fail("Unexpected receive")

    val result = PlacedValue.run[PeerB]:
      Choreography.run[PeerB]:
        val valueOnPeerA: Int on PeerA = on[PeerA](42)
        val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
        receivedValue.take

    result shouldBe 42
    (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 2 // Check reachable peers
    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Register the flow on the network
    (netEffect.receive[PeerA, PeerB, Id, Int](_: String, _: Reference)(using _: Decoder[Int])).times shouldBe 1 // Receive the value from peerA
  it should "send the value when the placed value is local" in:
    given network: Locix[NoOpIntNetwork](NoOpIntNetwork())

    val result = PlacedValue.run[PeerA]:
      Choreography.run[PeerA]:
        val valueOnPeerA: Int on PeerA = on[PeerA](42)
        val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
    network.effect.setValues should contain(42)
  it should "receive the value when the placed value is remote" in:
    given network: Locix[NoOpIntNetwork](NoOpIntNetwork())

    val result = PlacedValue.run[PeerB]:
      Choreography.run[PeerB]:
        val valueOnPeerA: Int on PeerA = on[PeerA](42)
        val receivedValue: Int on PeerB = comm[PeerA, PeerB](valueOnPeerA)
        receivedValue.take shouldBe 42
        ()
end ChoreographyTest
