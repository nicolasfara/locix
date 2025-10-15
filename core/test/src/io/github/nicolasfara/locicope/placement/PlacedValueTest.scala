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
import io.github.nicolasfara.locicope.placement.PlacedValue.{ asLocal, asLocalAll, unwrap, on }
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.network.Network.localId
import io.github.nicolasfara.locicope.placement.PlacementType.{on, Id}

class PlacedValueTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private trait IntNetwork extends Network.Effect:
    type Id = Int
    type NetworkError = Throwable
    type Address[P <: Peer] = String
  private val netEffect = stub[IntNetwork]
  private given net: Locicope[Network.Effect](netEffect)

  before:
    resetStubs()

  // "A `PlacedValue`" should "allow lifting a value produced in a peer scope into a placed value" in:
  //   (netEffect.register(_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("server"))
  //   (netEffect.send[Id, Int, Server, Client](_: String, _: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(Right(()))

  //   def placedValueProgram(using Network, PlacedValue) = on[Client](10)
  //   val result = PlacedValue.run[Client]:
  //     val unwrappedValue = placedValueProgram.unwrap
  //     unwrappedValue shouldBe 10
  //     unwrappedValue

  //   result shouldBe 10
  //   (netEffect.register(_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Register the value on the network
  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
  //   (netEffect.send(_: String, _: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1 // Send the value to the reachable peers

  // it should "allow retrieving a placed value from a remote peer" in:
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("peer1"))
  //   (netEffect.receive[Id, Int, Server, Client](_: String, _: Reference)(using _: Decoder[Int])).returnsWith(Right(10))

  //   def placedValueProgram(using Network, PlacedValue) = on[Server](10)
  //   val result = PlacedValue.run[Client]:
  //     val localValue = placedValueProgram.asLocal
  //     localValue shouldBe 10
  //     localValue

  //   result shouldBe 10
  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
  //   (netEffect
  //     .receive[Id, Int, Server, Client](_: String, _: Reference)(using _: Decoder[Int]))
  //     .times shouldBe 1 // Receive the value from the remote peer

  it should "allow retrieving a placed value from multiple remote peers" in:
    (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("client1", "client2"))
    (netEffect
      .getId(_: netEffect.Address[Client]))
      .returns:
        case "client1" => 1
        case "client2" => 2
        case clientNotFound => fail(s"Peer `${clientNotFound}` not registered in the network")
    (netEffect
      .receive[Id, Int, Client, Server](_: String, _: Reference)(using _: Decoder[Int]))
      .returns:
        case ("client1", _, _) => Right(1)
        case ("client2", _, _) => Right(2)
        case _ => Left(new NoSuchElementException("Peer not found"))

    def placedValueProgram(using Network, PlacedValue) = on[Client](Int.MaxValue)
    val result = PlacedValue.run[Server]:
      val localValues = placedValueProgram.asLocalAll
      localValues shouldBe Map(1 -> 1, 2 -> 2)
      localValues.values.sum

    result shouldBe 3
    (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
    (netEffect
      .receive[Id, Int, Client, Server](_: String, _: Reference)(using _: Decoder[Int]))
      .times shouldBe 2 // Receive the value from the remote peers
end PlacedValueTest
