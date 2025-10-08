package io.github.nicolasfara.locicope

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.Multitier.Multitier
import io.github.nicolasfara.locicope.utils.TestCodec.given
import io.github.nicolasfara.locicope.Multitier.placed
import io.github.nicolasfara.locicope.utils.ClientServerArch.{ Client, Server }
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.serialization.Encoder
import ox.flow.Flow
import io.github.nicolasfara.locicope.Multitier.placedFlow
import io.github.nicolasfara.locicope.PlacementType.*

class MultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[Net.Effect]
  given net: Locicope[Net.Effect](netEffect)

  before:
    resetStubs()

  "The Multitier capability" should "access a remote value" in:
    (netEffect.getValue(_: Reference)(using _: Decoder[Int])).returnsWith(Right(10))
    (netEffect
      .setValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .returns:
        case (10, _, _) => ()
        case _ @(wrongValue, _, _) => fail(s"Unexpected value: $wrongValue")

    def multitierSingleValue(using Net, Multitier) =
      val valueOnServer = placed[Server](10)
      placed[Client]:
        val localValue = valueOnServer.asLocal
        localValue shouldBe 10
        localValue

    Multitier.run[Client](multitierSingleValue)

    (netEffect.getValue(_: Reference)(using _: Decoder[Int])).times shouldBe 1
    (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1

  it should "access a remote flow" in:
    (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).returnsWith(Right(Flow.fromIterable(Seq(1, 2, 3))))
    (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).returnsWith(())

    def multitierFlow(using Net, Multitier) =
      val flowOnServer = placedFlow[Server](Flow.fromIterable(Seq(1, 2, 3)))
      placed[Client]:
        val localFlow = flowOnServer.asLocal
        val values = localFlow.runToList()
        values shouldBe List(1, 2, 3)
        values.sum

    Multitier.run[Client](multitierFlow)

    (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).times shouldBe 1
    (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1

  it should "access a local value without network calls" in:
    (netEffect
      .setValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .returns:
        case (42, _, _) => ()
        case _ @(wrongValue, _, _) => fail(s"Unexpected value: $wrongValue")

    def multitierLocalValue(using Net, Multitier) =
      val valueOnClient = placed[Client](42)
      placed[Client]:
        val localValue = valueOnClient.unwrap
        localValue shouldBe 42
        localValue

    Multitier.run[Client](multitierLocalValue)

    (netEffect.getValue(_: Reference)(using _: Decoder[Int])).times shouldBe 0
    (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 2

  it should "access a local flow without network calls" in:
    (netEffect
      .setValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .returns:
        case (15, _, _) => ()
        case _ @(wrongValue, _, _) => fail(s"Unexpected value: $wrongValue")
    (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

    def multitierLocalFlow(using Net, Multitier) =
      val flowOnClient = placedFlow[Client](Flow.fromIterable(Seq(4, 5, 6)))
      placed[Client]:
        val localFlow = flowOnClient.unwrap
        val values = localFlow.runToList()
        values shouldBe List(4, 5, 6)
        values.sum

    Multitier.run[Client](multitierLocalFlow)
    (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).times shouldBe 0
    (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).times shouldBe 1
    (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1
end MultitierTest
