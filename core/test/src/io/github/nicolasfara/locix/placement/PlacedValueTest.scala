package io.github.nicolasfara.locix.placement

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.Locix
import io.github.nicolasfara.locix.placement.Peers.Peer
import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.placement.Peers.TiedWith
import io.github.nicolasfara.locix.serialization.Decoder
import io.github.nicolasfara.locix.serialization.Encoder
import io.github.nicolasfara.locix.placement.Peers.PeerRepr
import io.github.nicolasfara.locix.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locix.utils.ClientServerArch.{ Client, Server }
import io.github.nicolasfara.locix.utils.TestCodec.given
import io.github.nicolasfara.locix.placement.PlacedValue.{ on, take }
import io.github.nicolasfara.locix.placement.PlacementType.PeerScope
import io.github.nicolasfara.stub.{ Id, IntNetwork }
import io.github.nicolasfara.locix.placement.PlacedValue

class PlacedValueTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private val netEffect = stub[IntNetwork]
  private given net: Locix[Network.Effect](netEffect)

  before:
    resetStubs()

  // Commented due to a bug in Scalamock: https://github.com/scalamock/scalamock/issues/696
  "A `PlacedValue`" should "allow lifting a value produced in a peer scope into a placed value" in:
    (netEffect.register(_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())
    (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("server"))
    (netEffect.send[Server, Client, Int](_: String, _: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(Right(()))

    def placedValueProgram(using Network, PlacedValue) = on[Client](10)
    val result = PlacedValue.run[Client]:
      val unwrappedValue = placedValueProgram.take
      unwrappedValue shouldBe 10
      unwrappedValue

    result shouldBe 10
    (netEffect.register(_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Register the value on the network
    (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
    (netEffect.send(_: String, _: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Send the value to the reachable peers
end PlacedValueTest
