package io.github.nicolasfara.locicope.multiparty.choreography

import io.github.nicolasfara.locicope.multiparty.choreography.Choreography.*
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.placement.PlacementType.{ on, given }
import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.utils.TestCodec.given

class ChoreographyTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  import io.github.nicolasfara.locicope.utils.ClientServerArch.*

  trait NetworkInt extends Network:
    type ID = Int

  private val net = stub[NetworkInt]

  before:
    resetStubs()

  "The Choreography capability" should "allow retrieving through the network a remote value after communication" in:
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllValues(_: Reference)(using _: Decoder[Int]))
      .returns:
        case (Reference(_, _, _), _) => Map(1 -> 10)

    Choreography.run[Server](using net): choreo ?=>
      val valueOnClient: Int on Client = at[Client](using net)(10)
      val valueOnServer: Int on Server = comm[Client, Server](using choreo, net)(valueOnClient)
      at[Server](using net): ctx ?=>
        val decodedValue = valueOnServer.unwrap(using summon, summon, net, ctx)
        decodedValue shouldBe Map(1 -> 10)
        decodedValue

    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1 // Register value on `at`
    (net.getAllValues(_: Reference)(using _: Decoder[Int])).times shouldBe 1 // Retrieve value on `comm`
  it should "register in the network a value ready to be communicated" in:
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllValues(_: Reference)(using _: Decoder[Int]))
      .returns:
        case (Reference(_, _, _), _) => Map(1 -> 10)

    Choreography.run[Client](using net): choreo ?=>
      val valueOnClient: Int on Client = at[Client](using net)(10)
      val valueOnServer: Int on Server = comm[Client, Server](using choreo, net)(valueOnClient)
      at[Server](using net): ctx ?=>
        val decodedValue = valueOnServer.unwrap(using summon, summon, net, ctx)
        decodedValue shouldBe Map(1 -> 10)
        decodedValue

    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 2 // Register value on `at` and `comm`
    (net.getValue(_: Reference)(using _: Decoder[Int])).times shouldBe 0 // No retrieval on `Client` side
  it should "allow retrieving multiple remote value when tied to multiple peers" in:
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getAllValues(_: Reference)(using _: Decoder[Int]))
      .returns:
        case (Reference(_, _, _), _) => Map(1 -> 10, 2 -> 20)

    Choreography.run[Server](using net): choreo ?=>
      val valueOnClient: Int on Client = at[Client](using net)(10)
      val valueOnServer: Int on Server = comm[Client, Server](using choreo, net)(valueOnClient)
      at[Server](using net): ctx ?=>
        val decodedValues = valueOnServer.unwrap(using summon, summon, net, ctx)
        decodedValues shouldBe Map(1 -> 10, 2 -> 20)
        decodedValues

    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1 // Register value on `at`
    (net.getAllValues(_: Reference)(using _: Decoder[Int])).times shouldBe 1 // Retrieve values on `comm`
end ChoreographyTest
