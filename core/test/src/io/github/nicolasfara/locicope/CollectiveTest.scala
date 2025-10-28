package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Collective.{ collective, neighbors, repeat, take, Collective, OutboundMessage }
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
// import io.github.nicolasfara.locicope.PlacementType.{ on, unwrap }
// import io.github.nicolasfara.locicope.network.NetworkResource.Reference
// import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
// import io.github.nicolasfara.locicope.utils.CpsArch.Smartphone
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.stub.IntNetwork
import ox.flow.Flow
import io.github.nicolasfara.locicope.placement.PlacementType.on
import io.github.nicolasfara.locicope.placement.PlacedFlow
import io.github.nicolasfara.locicope.utils.CpsArch.*
import io.github.nicolasfara.locicope.placement.PlacedFlow.PlacedFlow
import io.github.nicolasfara.locicope.utils.TestCodec.given
// import ox.flow.Flow

import scala.concurrent.duration.DurationInt
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.serialization.Encoder

class CollectiveTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[IntNetwork]
  given net: Locicope[Network.Effect](netEffect)

  before:
    resetStubs()

  "The `Collective` capability" should "return an empty export when no spatial operator are used" in:
    def temporalEvolution(using Network, Collective, PlacedFlow): Flow[Int] on Smartphone = collective(1.second):
      repeat(0)(_ + 1)
    // (netEffect.register[Flow, Int](_: Reference, _: Flow[Int])(using _: Encoder[Int])).returnsWith(())

    val result = PlacedFlow.run:
      Collective.run[Smartphone]:
        val res = take(temporalEvolution)
        res.take(5).runToList() shouldBe List(1, 2, 3, 4, 5)

//   "A collective program" should "return an empty export when no spatial operator are used" in:
//     def temporalEvolution(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
//       repeat(0)(_ + 1)
//     // Setup stubs
//     var lastExport: OutboundMessage = null
//     (() => netEffect.id).returnsWith(1)
//     (netEffect.getValues(_: Reference)(using _: Decoder[OutboundMessage])).returnsWith(Right(Map.empty))
//     (netEffect.setValue(_: OutboundMessage, _: Reference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

//     Collective.run[Smartphone](using net):
//       val res = temporalEvolution(using net, summon)
//       res.unwrap.take(5).runToList() shouldBe List(1, 2, 3, 4, 5)

//     lastExport shouldBe Map.empty // No export since no spatial operator is used

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
