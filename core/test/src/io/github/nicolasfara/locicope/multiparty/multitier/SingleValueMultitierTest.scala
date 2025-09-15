package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.placement.PlacementType.{ on, given }
import io.github.nicolasfara.locicope.utils.TestCodec
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.multiparty.multitier.Multitier.*
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Placeable
import io.github.nicolasfara.locicope.serialization.{ Codec, Decoder, Encoder }
import io.github.nicolasfara.locicope.utils.TestCodec.given
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import ox.flow.Flow

class SingleValueMultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
  import io.github.nicolasfara.locicope.utils.ClientServerArch.*

  private val net = stub[Network]

  before:
    resetStubs()

  "The Multitier capability operating on single value" should "access a remote value" in:
    // Setup the network to return a value when requested
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getValue(_: Reference)(using _: Decoder[Int]))
      .returns:
        case (Reference(_, _, _), _) => Right(10)

    Multitier.run[Client](using net): mt ?=>
      val valueOnServer: Int on Server = placed[Server](using net)(10)
      placed[Client](using net): ml ?=>
        val localValue = valueOnServer.asLocal(using summon, summon, net, mt, ml)
        localValue shouldBe 10
        localValue
    // Verify that the network methods were called properly
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1
    (net.getValue(_: Reference)(using _: Decoder[Int])).times shouldBe 1
  it should "access a remote flow" in:
    // Setup the network to return a flow when requested
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net
      .getFlow(_: Reference)(using _: Decoder[Int]))
      .returns:
        case (Reference(_, _, _), _) => Right(Flow.fromIterable(Seq(1, 2, 3)))

    Multitier.run[Client](using net):
      val flowOnServer: Flow[Int] on Server = placedFlow[Server](using net)(Flow.fromIterable(Seq(1, 2, 3)))
      placed[Client](using net):
        val localFlow = flowOnServer.asLocal(using summon, summon, net, summon)
        val values = localFlow.runToList()
        values shouldBe List(1, 2, 3)
        values.sum
    // Verify that the network methods were called properly
    (net.registerFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).times shouldBe 0 // On client, no flow registered
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1
    (net.getFlow(_: Reference)(using _: Decoder[Int])).times shouldBe 1
  it should "access single local value" in:
    // Setup the network to return a value when requested
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())

    Multitier.run[Client](using net):
      val valueOnClient: Int on Client = placed[Client](using net)(20)
      placed[Client](using net):
        val localValue = valueOnClient.unwrap(using summon, summon, net, summon)
        localValue shouldBe 20
        localValue
    // Verify that the network methods were called properly
    (net
      .registerValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .times shouldBe 2 // Once for the valueOnClient and once for the placed value
    (net.getValue(_: Reference)(using _: Decoder[Int])).times shouldBe 0 // No remote call for local value
  it should "access a local flow" in:
    // Setup the network to return a flow when requested
    (net.registerFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returns(_ => ())
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())

    Multitier.run[Client](using net):
      val flowOnClient: Flow[Int] on Client = placedFlow[Client](using net)(Flow.fromIterable(Seq(4, 5, 6)))
      placed[Client](using net):
        val localFlow = flowOnClient.unwrap(using summon, summon, net, summon)
        val values = localFlow.runToList()
        values shouldBe List(4, 5, 6)
        values.sum
    // Verify that the network methods were called properly
    (net
      .registerFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int]))
      .times shouldBe 1
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1
  it should "call a placed function on a local peer" in:
    // Setup the network to register a function
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Client])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .returns(_ => ())
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())

    def placedOnServer(using Network, Multitier) = function[(Int, Int), Int, on, Client]:
      case (x: Int, y: Int) => x + y

    Multitier.run[Client](using net):
      placed[Client](using net):
        val result = placedOnServer(using net, summon)((10, 10)).unwrap(using summon, summon, net, summon)
        result shouldBe 20
        10
    // Verify that the network methods were called properly
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Client])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .times shouldBe 0 // The function is local, so no registration needed
    (net
      .registerValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .times shouldBe 2 // Function-call lifts the value, and registers it into the network
  it should "call a placed function on a remote peer" in:
    // Setup the network to register a function
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Server])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .returns(_ => ())
    (net
      .callFunction(_: (Int, Int), _: Reference)(using _: Codec[(Int, Int)], _: Codec[Int], _: Placeable[on]))
      .returns(_ => 20) // Simulate the function call returning 20
    (net.registerValue(_: Int, _: Reference)(using _: Encoder[Int])).returns(_ => ())

    def placedOnServer(using Network, Multitier) = function[(Int, Int), Int, on, Server]:
      case (x: Int, y: Int) => x + y

    Multitier.run[Client](using net): mt ?=>
      placed[Client](using net): ml ?=>
        val result = placedOnServer(using net, summon)((10, 10)).asLocal(using summon, summon, net, mt, ml)
        result shouldBe 20
        10
    // Verify that the network methods were called properly
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Server])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .times shouldBe 0 // The function is remote, so no registration needed
    (net
      .registerValue(_: Int, _: Reference)(using _: Encoder[Int]))
      .times shouldBe 2 // Function-call lifts the value, and registers it into the network
  it should "register into the network a local function called on a remote placement block" in:
    // Setup the network to register a function
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Server])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .returns(_ => ())

    def placedOnServer(using Network, Multitier) = function[(Int, Int), Int, on, Server]:
      case (x: Int, y: Int) => x + y

    Multitier.run[Server](using net): mt ?=>
      placed[Client](using net): ml ?=>
        placedOnServer(using net, summon)((10, 10)).asLocal(using summon, summon, net, mt, ml)
    // Verify that the network methods were called properly
    (net
      .registerFunction(_: Multitier#PlacedFunction[(Int, Int), Int, on, Server])(using
        _: Encoder[(Int, Int)],
        _: Encoder[Int],
      ))
      .times shouldBe 1 // The function is remote and registered into the network
end SingleValueMultitierTest
