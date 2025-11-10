package io.github.nicolasfara.locicope.placement

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import io.github.nicolasfara.stub.{ Id, IntNetwork }
import io.github.nicolasfara.locicope.Locicope
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.Peers.{ PeerRepr, TiedWith }
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locicope.utils.ClientServerArch.{ Client, Server }
import io.github.nicolasfara.locicope.placement.Peers.peer
import io.github.nicolasfara.locicope.network.NetworkResource.ValueType
import io.github.nicolasfara.locicope.utils.TestCodec.{ foo, given }
import io.github.nicolasfara.locicope.placement.PlacedFlow.flowOn
import io.github.nicolasfara.locicope.placement.PlacementType.PeerScope
import io.github.nicolasfara.locicope.placement.PlacementType.on
import ox.flow.Flow

class PlacedFlowTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  private val netEffect = stub[IntNetwork]
  private given net: Locicope[Network.Effect](netEffect)

  before:
    resetStubs()

  // Commented due to a bug in Scalamock: https://github.com/scalamock/scalamock/issues/696
  // "A `PlacedFlow`" should "allow lifting a flow produced in a peer scope into a placed flow" in:
  //   (netEffect.register(_: Reference, _: Flow[Int])(using _: Encoder[Int])).returnsWith(())
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("server"))
  //   (netEffect.send[Server, Client, Int](_: String, _: Reference, _: Int)(using _: Encoder[Int])).returnsWith(Right(()))

  //   def placedFlowProgram(using Network, PlacedFlow) = flowOn[Client](Flow.fromIterable(Seq(1, 2, 3)))
  //   val result = PlacedFlow.run[Client]:
  //     val unwrappedFlow = placedFlowProgram.unwrap
  //     unwrappedFlow shouldBe a [Flow[_]]
  //     42

  //   result shouldBe 42
  //   (netEffect.register(_: Reference, _: Flow[Int])(using _: Encoder[Flow[Int]])).times shouldBe 1 // Register the flow on the network
  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
  //   (netEffect.send(_: String, _: Reference, _: Flow[Int])(using _: Encoder[Flow[Int]])).times shouldBe 1 // Send the flow to the reachable peers

  // it should "allow retrieving a placed flow from a remote peer" in:
  //   val testFlow = Flow.fromIterable(Seq(10, 20, 30))
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("peer1"))
  //   (netEffect.receive[Flow, Int, Server, Client](_: String, _: Reference)(using _: Decoder[Int])).returnsWith(Right(testFlow))

  //   def placedFlowProgram(using Network, PlacedFlow) = flowOn[Server](Flow.fromIterable(Seq(10, 20, 30)))
  //   val result = PlacedFlow.run[Client]:
  //     val localFlow = placedFlowProgram.asLocal
  //     localFlow shouldBe testFlow
  //     42

  //   result shouldBe 42
  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
  //   (netEffect
  //     .receive[Flow, Int, Server, Client](_: String, _: Reference)(using _: Decoder[Int]))
  //     .times shouldBe 1 // Receive the flow from the remote peer

  // it should "allow retrieving a placed flow from multiple remote peers using asLocalAll" in:
  //   val testFlow1 = Flow.fromIterable(Seq(10, 20))
  //   val testFlow2 = Flow.fromIterable(Seq(30, 40))
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("client1", "client2"))
  //   (netEffect
  //     .getId(_: netEffect.Address[Client]))
  //     .returns:
  //       case "client1" => 1
  //       case "client2" => 2
  //       case clientNotFound => fail(s"Peer `${clientNotFound}` not registered in the network")
  //   (netEffect
  //     .receive[Flow, Int, Client, Server](_: String, _: Reference)(using _: Decoder[Int]))
  //     .returns:
  //       case ("client1", _, _) => Right(testFlow1)
  //       case ("client2", _, _) => Right(testFlow2)
  //       case _ => Left(new NoSuchElementException("Peer not found"))

  //   def placedFlowProgram(using Network, PlacedFlow) = flowOn[Client](Flow.fromIterable(Seq(Int.MaxValue)))
  //   val result = PlacedFlow.run[Server]:
  //     val localFlows = placedFlowProgram.asLocalAll
  //     localFlows shouldBe a[Flow[Int]]
  //     42

  //   result shouldBe 42
  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
  //   (netEffect
  //     .receive[Flow, Int, Client, Server](_: String, _: Reference)(using _: Decoder[Int]))
  //     .times shouldBe 2 // Receive the flow from the remote peers

  // it should "throw an exception when asLocal is called with multiple remote peers" in:
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("peer1", "peer2"))

  //   def placedFlowProgram(using pf: PlacedFlow, net: Network) = flowOn[Server](Flow.fromIterable(Seq(10)))
  //   assertThrows[IllegalStateException]:
  //     PlacedFlow.run[Client][Int]:
  //       placedFlowProgram.asLocal
  //       42

  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers

  // it should "throw an exception when asLocal is called with no remote peers" in:
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set.empty)

  //   def placedFlowProgram(using pf: PlacedFlow, net: Network) = flowOn[Server](Flow.fromIterable(Seq(10)))
  //   assertThrows[IllegalStateException]:
  //     PlacedFlow.run[Client][Int]:
  //       placedFlowProgram.asLocal
  //       42

  //   (netEffect.reachablePeersOf(_: PeerRepr)).times shouldBe 1 // Check reachable peers
end PlacedFlowTest
