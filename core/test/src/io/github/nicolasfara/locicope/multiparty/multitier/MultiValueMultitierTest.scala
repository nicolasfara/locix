package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.placement.PlacementType.{ on, given }
import io.github.nicolasfara.locicope.utils.TestCodec
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.multiparty.multitier.Multitier.*
import io.github.nicolasfara.locicope.network.NetworkResource.ResourceReference
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import io.github.nicolasfara.locicope.utils.TestCodec.given
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import ox.flow.Flow

class MultiValueMultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  import io.github.nicolasfara.locicope.utils.ClientServerArch.*

  trait NetworkInt extends Network:
    type ID = Int

  private val net = stub[NetworkInt]

  before:
    resetStubs()

  "The Multitier capability operating on multiple values" should "return empty map when no reachable devices in the network" in:
    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllValues(_: ResourceReference)(using _: Decoder[Int]))
      .returns:
        case (ResourceReference(_, _, _), _) => Map.empty

    multitier[Server](using net): mt ?=>
      val valueOnClient: Int on Client = placed[Client](using net)(10)
      placed[Server](using net): ctx ?=>
        val localValue = valueOnClient.asLocalAll(using summon, summon, net, mt, ctx)
        localValue shouldBe Map.empty
        Int.MaxValue

    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).times shouldBe 1
    (net.getAllValues(_: ResourceReference)(using _: Decoder[Int])).times shouldBe 1
  it should "return a map with values from all reachable devices in the network" in:
    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllValues(_: ResourceReference)(using _: Decoder[Int]))
      .returns:
        case (ResourceReference(_, _, _), _) => Map(1 -> 10, 2 -> 10, 3 -> 10)

    multitier[Server](using net): mt ?=>
      val valueOnClient: Int on Client = placed[Client](using net)(10)
      placed[Server](using net): ctx ?=>
        val localValue = valueOnClient.asLocalAll(using summon, summon, net, mt, ctx)
        localValue.values.toList shouldBe List(10, 10, 10)
        localValue.keys shouldBe Set(1, 2, 3)
        Int.MaxValue

    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).times shouldBe 1
    (net.getAllValues(_: ResourceReference)(using _: Decoder[Int])).times shouldBe 1
  it should "return a flow which is the combination of the flows returned by the connected peers" in:
    val sequenceToReturn = Seq((1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3), (3, 3))
    (net.registerFlow(_: Flow[Int], _: ResourceReference)(using _: Encoder[Int])).returns(_ => ())
    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllFlows(_: ResourceReference)(using _: Decoder[Int]))
      .returns:
        case (ResourceReference(_, _, _), _) => Right(Flow.fromIterable(sequenceToReturn))

    multitier[Server](using net):
      val flowOnClient: Flow[Int] on Client = placedFlow[Client](using net)(Flow.fromIterable(Seq(1, 2, 3)))
      placed[Server](using net): ctx ?=>
        val localFlow = flowOnClient.asLocalAll(using summon, summon, net, summon)
        val values = localFlow.runToList()
        values shouldBe sequenceToReturn.toList
        values.map(_._2).sum

    (net.registerFlow(_: Flow[Int], _: ResourceReference)(using _: Encoder[Int])).times shouldBe 0 // On server, no flow registered
    (net.registerValue(_: Int, _: ResourceReference)(using _: Encoder[Int])).times shouldBe 1
    (net.getAllFlows(_: ResourceReference)(using _: Decoder[Int])).times shouldBe 1
end MultiValueMultitierTest
