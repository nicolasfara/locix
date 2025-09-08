package io.github.nicolasfara.locicope

import io.github.nicolasfara.locicope.Collective.{ collective, neighbors, repeat, Collective, OutboundMessage }
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.PlacementType.{ on, unwrap }
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import io.github.nicolasfara.locicope.utils.CpsArch.Smartphone
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.utils.TestCodec.given
import ox.flow.Flow

import scala.concurrent.duration.DurationInt

class CollectiveTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[Net.Effect]
  given net: Locicope[Net.Effect](netEffect)

  before:
    resetStubs()

  "A collective program" should "return an empty export when no spatial operator are used" in:
    def temporalEvolution(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
      repeat(0)(_ + 1)
    // Setup stubs
    var lastExport: OutboundMessage = null
    (() => netEffect.id).returnsWith(1)
    (netEffect.getValues(_: ResourceReference)(using _: Decoder[OutboundMessage])).returnsWith(Right(Map.empty))
    (netEffect.setValue(_: OutboundMessage, _: ResourceReference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
    (netEffect.setFlow(_: Flow[Int], _: ResourceReference)(using _: Encoder[Int])).returnsWith(())

    Collective.run[Smartphone](using net):
      val res = temporalEvolution(using net, summon)
      res.unwrap.take(5).runToList() shouldBe List(1, 2, 3, 4, 5)

    lastExport shouldBe Map.empty // No export since no spatial operator is used

  it should "return an export with the value tree produced by spatial operators" in:
    def spatialComputation(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
      neighbors(1).local

    // Setup stubs
    var lastExport: OutboundMessage = null
    (() => netEffect.id).returnsWith(1)
    (netEffect.getValues(_: ResourceReference)(using _: Decoder[OutboundMessage])).returnsWith(Right(Map.empty))
    (netEffect.setValue(_: OutboundMessage, _: ResourceReference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
    (netEffect.setFlow(_: Flow[Int], _: ResourceReference)(using _: Encoder[Int])).returnsWith(())

    Collective.run[Smartphone](using net):
      val res = spatialComputation(using net, summon)
      res.unwrap.take(5).runToList() shouldBe List(1, 1, 1, 1, 1) // Each round the value is the same since neighbors return the same values

    val expectExport = Map("neighbors.0" -> summon[Encoder[Int]].encode(1))
    lastExport.forall { case (key, bytes) => expectExport.get(key).exists(bytes.sameElements) } shouldBe true

  it should "build a field with neighbors values aligned to the operator" in:
    def spatialComputation(using Net, Collective): Flow[Int] on Smartphone = collective(1.second):
      neighbors(1).sum

    // Setup stubs
    var lastExport: OutboundMessage = null
    val neighborValues = Map(
      1 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(1)),
      2 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(2)),
      3 -> Map("neighbors.0" -> summon[Encoder[Int]].encode(3)),
    )
    (() => netEffect.id).returnsWith(1)
    (netEffect.getValues(_: ResourceReference)(using _: Decoder[OutboundMessage])).returnsWith(Right(neighborValues))
    (netEffect.setValue(_: OutboundMessage, _: ResourceReference)(using _: Encoder[OutboundMessage])).returns(outbound => lastExport = outbound._1)
    (netEffect.setFlow(_: Flow[Int], _: ResourceReference)(using _: Encoder[Int])).returnsWith(())

    Collective.run[Smartphone](using net):
      val res = spatialComputation(using net, summon)
      res.unwrap.take(1).runToList() shouldBe List(7) // Summed values from neighbors (1 + 2 + 3 + local 1)

    val expectExport = Map("neighbors.0" -> summon[Encoder[Int]].encode(1))
    lastExport.forall { case (key, bytes) => expectExport.get(key).exists(bytes.sameElements) } shouldBe true
end CollectiveTest
