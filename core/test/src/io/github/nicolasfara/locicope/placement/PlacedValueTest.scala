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
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.utils.ClientServerArch.Client
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import io.github.nicolasfara.locicope.utils.TestCodec.{foo, given}
import io.github.nicolasfara.locicope.placement.PlacedValue.unwrap

class PlacedValueTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private trait IntNetwork extends Network.Effect:
    type Id = Int
    type NetworkError = Throwable
    type Address[P <: Peer] = String
  private val netEffect = stub[IntNetwork]
  private given net: Locicope[Network.Effect](netEffect)

  before:
    resetStubs()

  "A `PlacedValue`" should "allow lifting a value produced in a peer scope into a placed value" in:
    def placedValueProgram(using Network, PlacedValue) = PlacedValue.on[Client, Int](10)
    def placedValueProgramDouble(using Network, PlacedValue) = PlacedValue.on[Client, Double](10.0)
    PlacedValue.run[Client]:
      placedValueProgram.unwrap shouldBe 10
      placedValueProgramDouble.unwrap shouldBe 10.0
      10
    ???
