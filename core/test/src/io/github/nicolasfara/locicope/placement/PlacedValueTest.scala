package io.github.nicolasfara.locicope.placement

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.serialization.Encoder
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.TiedWith
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.utils.ClientServerArch.{ Client, Server }
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import io.github.nicolasfara.locicope.utils.TestCodec.{ foo, given }
import io.github.nicolasfara.locicope.placement.PlacedValue.{ on, take }
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.network.Network.localAddress
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.stub.{ Id, IntNetwork }

class PlacedValueTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private val netEffect = stub[IntNetwork]
  private given net: Locicope[Network.Effect](netEffect)

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
