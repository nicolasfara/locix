package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.network.Network
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.stub.IntNetwork

import io.github.nicolasfara.locix.network.NetworkResource.Reference
import io.github.nicolasfara.locix.serialization.Encoder
import io.github.nicolasfara.locix.placement.Peers.PeerRepr
import io.github.nicolasfara.locix.serialization.Decoder
import io.github.nicolasfara.locix.Locix

class CollectiveTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[IntNetwork]
  given net: Locix[Network.Effect](netEffect)

  before:
    resetStubs()

  // "The `Collective` capability" should "return an empty export when no spatial operators are used" in:
  //   (netEffect.register[Flow, Int](_: Reference, _: Flow[Int])(using _: Encoder[Int])).returnsWith(())
  //   (netEffect.reachablePeersOf(_: PeerRepr)).returnsWith(Set("n1", "n2"))
  //   (netEffect.receive[Smartphone, Smartphone, [X] =>> Map[X, Array[Byte]] | Flow[X], Int](_: String, _: Reference)(using _: Decoder[Int])).returns:
  //     case (_, Reference(id, _, _), _) if id.contains("Outbound") => Right(Map.empty)
  //     case _ => Right(Flow.fromIterable(List(1, 2, 3, 4, 5)))
  //   (netEffect.getId(_: String)).returnsWith(0)
  //   (() => netEffect.localAddress[Smartphone]).returnsWith("local")
  //   (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).returnsWith(Right(()))

  //   def temporalEvolution(using Network, Collective, PlacedFlow): Flow[Int] on Smartphone = collective(1.second):
  //     repeat(0)(_ + 1)

  //   val result = PlacedFlow.run[Smartphone]:
  //     Collective.run[Smartphone]:
  //       val res = take(temporalEvolution)
  //       res.take(5).runToList() shouldBe List(1, 2, 3, 4, 5)

  // it should "return an export with the value tree produced by spatial operators" in:
  //   def spatialComputation(using Network, Collective, PlacedFlow): Flow[Int] on Smartphone = collective(1.second):
  //     neighbors(1).local

  //   val result = PlacedFlow.run:
  //     Collective.run[Smartphone]:
  //       val res = take(spatialComputation)
  //       res.take(5).runToList() shouldBe List(1, 1, 1, 1, 1)

  // it should "build a field with neighbors values aligned to the operator" in:
  //   def spatialComputation(using Network, Collective, PlacedFlow): Flow[Int] on Smartphone = collective(1.second):
  //     neighbors(1).local

  //   val result = PlacedFlow.run:
  //     Collective.run[Smartphone]:
  //       val res = take(spatialComputation)
  //       res.take(5).runToList() shouldBe List(1, 1, 1, 1, 1)

  // it should "partition the network with device aligned to the branch condition" in:
  //   def spatialComputation(using Network, Collective, PlacedFlow): Flow[Int] on Smartphone = collective(1.second):
  //     branch(localId.asInstanceOf[Int] % 2 == 0) { neighbors(1).sum } { neighbors(2).sum }

  //   val result = PlacedFlow.run:
  //     Collective.run[Smartphone]:
  //       val res = take(spatialComputation)
  //       res.take(1).runToList() shouldBe List(2)
end CollectiveTest

//   it should "return an export with the value tree produced by spatial operators" in:
//     def spatialComputation(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
//       neighbors(1).local

//     // Setup stubs
//     var lastExport: OutboundMessage = null
//     (() => netEffect.id).returnsWith(1)
//     (netEffect.getValues(_: Reference)(using _: Decoder[OutboundMessage])).returnsWith(Right(Map.empty))
//     (netEffect.setValue(_: OutboundMessage, _: Reference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

//     Collective.run[Smartphone](using net):
//       val res = spatialComputation(using net, summon)
//       res.unwrap.take(5).runToList() shouldBe List(1, 1, 1, 1, 1) // Each round the value is the same since neighbors return the same values

//     val expectExport = Map("neighbors.0" -> summon[Encoder[Int]].encode(1))
//     lastExport.forall { case (key, bytes) => expectExport.get(key).exists(bytes.sameElements) } shouldBe true

//   it should "build a field with neighbors values aligned to the operator" in:
//     def spatialComputation(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
//       neighbors(1).sum

//     // Setup stubs
//     var lastExport: OutboundMessage = null
//     val neighborValues = Map(
//       1 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(1)),
//       2 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(2)),
//       3 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(3)),
//     )
//     (() => netEffect.id).returnsWith(1)
//     (netEffect.getValues(_: Reference)(using _: Decoder[OutboundMessage])).returnsWith(Right(neighborValues))
//     (netEffect.setValue(_: OutboundMessage, _: Reference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

//     Collective.run[Smartphone](using net):
//       val res = spatialComputation(using net, summon)
//       res.unwrap.take(1).runToList() shouldBe List(7) // Summed values from neighbors (1 + 2 + 3 + local 1)

//     val expectExport = Map("neighbors.0" -> summon[Encoder[Int]].encode(1))
//     lastExport.forall { case (key, bytes) => expectExport.get(key).exists(bytes.sameElements) } shouldBe true

//   it should "partition the network with device aligned to the branch condition" in:
//     def spatialComputation(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
//       Collective.branch(summon[Collective].effect.localId % 2 == 0) { neighbors(1).sum } { neighbors(2).sum }

//     // Setup stubs
//     var lastExport: OutboundMessage = null
//     val neighborValues = Map(
//       1 -> Map("branch[false].0/neighbors.0" -> summon[Encoder[Int]].encode(2)),
//       2 -> Map("branch[true].0/neighbors.0" -> summon[Encoder[Int]].encode(1)),
//       3 -> Map("branch[false].0/neighbors.0" -> summon[Encoder[Int]].encode(2)),
//     )
//     (() => netEffect.id).returnsWith(0) // Even id
//     (netEffect.getValues(_: Reference)(using _: Decoder[OutboundMessage])).returnsWith(Right(neighborValues))
//     (netEffect.setValue(_: OutboundMessage, _: Reference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

//     Collective.run[Smartphone](using net):
//       val res = spatialComputation(using net, summon)
//       res.unwrap.take(1).runToList() shouldBe List(2) // Each round the value is the same since branch return the same values

//     val expectExport = Map("branch[true].0/neighbors.0" -> summon[Encoder[Int]].encode(1))
//     lastExport.forall { case (key, bytes) => expectExport.get(key).exists(bytes.sameElements) } shouldBe true
// end CollectiveTest
