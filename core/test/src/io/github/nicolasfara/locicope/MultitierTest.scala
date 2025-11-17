package io.github.nicolasfara.locicope

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.Multitier.Multitier
import io.github.nicolasfara.locicope.utils.TestCodec.given
import io.github.nicolasfara.locicope.utils.ClientServerArch.{ Client, Server }
import io.github.nicolasfara.locicope.network.NetworkResource.Reference
import io.github.nicolasfara.locicope.serialization.Decoder
import io.github.nicolasfara.locicope.serialization.Encoder
import ox.flow.Flow
import io.github.nicolasfara.stub.IntNetwork
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.network.Network.Network
import io.github.nicolasfara.locicope.placement.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue.PlacedValue
import io.github.nicolasfara.locicope.placement.PlacedValue.on
import io.github.nicolasfara.locicope.Multitier.asLocal
import io.github.nicolasfara.locicope.utils.TestCodec.given
import io.github.nicolasfara.locicope.placement.Peers.PeerRepr
import io.github.nicolasfara.locicope.placement.PlacedValue.take

class MultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[IntNetwork]
  given net: Locicope[Network.Effect](netEffect)
  type Id[V] = V

  before:
    resetStubs()

  "The Multitier capability" should "access a remote value" in:
    (netEffect.receive[Server, Client, [X] =>> X, Int](_: String, _: Reference)(using _: Decoder[Int])).returnsWith(Right(10))
    (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).returnsWith(Right(()))
    (netEffect.reachablePeersOf[Server](_: PeerRepr)).returnsWith(Set("server1"))
    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())

    def multitierSingleValue(using Network, PlacedValue, Multitier) =
      val valueOnServer = on[Server](10)
      on[Client]:
        val localValue = asLocal(valueOnServer)
        localValue shouldBe 10
        localValue
      ()

    PlacedValue.run[Client]:
      Multitier.run[Client](multitierSingleValue)

    (netEffect.receive[Server, Client, [X] =>> X, Int](_: String, _: Reference)(using _: Decoder[Int])).times shouldBe 1
    (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).times shouldBe 1
    (netEffect.reachablePeersOf[Server](_: PeerRepr)).times shouldBe 2
    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 1
//   it should "access a remote flow" in:
//     (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).returnsWith(Right(Flow.fromIterable(Seq(1, 2, 3))))
//     (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).returnsWith(())

//     def multitierFlow(using Net, Multitier) =
//       val flowOnServer = placedFlow[Server](Flow.fromIterable(Seq(1, 2, 3)))
//       placed[Client]:
//         val localFlow = flowOnServer.asLocal
//         val values = localFlow.runToList()
//         values shouldBe List(1, 2, 3)
//         values.sum

//     Multitier.run[Client](multitierFlow)

//     (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).times shouldBe 1
//     (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1

  it should "access a local value without network calls" in:
    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).returnsWith(())
    (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).returnsWith(Right(()))
    (netEffect.reachablePeersOf[Server](_: PeerRepr)).returnsWith(Set("server1"))

    def multitierLocalValue(using Network, PlacedValue, Multitier) =
      val valueOnClient = on[Client](42)
      on[Client]:
        val localValue = valueOnClient.take
        localValue shouldBe 42
        localValue
      ()

    PlacedValue.run[Client]:
      Multitier.run[Client](multitierLocalValue)

    (netEffect.register[Id, Int](_: Reference, _: Id[Int])(using _: Encoder[Int])).times shouldBe 2
    (netEffect.reachablePeersOf[Server](_: PeerRepr)).times shouldBe 2
    (netEffect.send(_: String, _: Reference, _: Int)(using _: Encoder[Int])).times shouldBe 2
end MultitierTest
//   it should "access a local flow without network calls" in:
//     (netEffect
//       .setValue(_: Int, _: Reference)(using _: Encoder[Int]))
//       .returns:
//         case (15, _, _) => ()
//         case _ @(wrongValue, _, _) => fail(s"Unexpected value: $wrongValue")
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).returnsWith(())

//     def multitierLocalFlow(using Net, Multitier) =
//       val flowOnClient = placedFlow[Client](Flow.fromIterable(Seq(4, 5, 6)))
//       placed[Client]:
//         val localFlow = flowOnClient.unwrap
//         val values = localFlow.runToList()
//         values shouldBe List(4, 5, 6)
//         values.sum

//     Multitier.run[Client](multitierLocalFlow)
//     (netEffect.getFlow(_: Reference)(using _: Decoder[Int])).times shouldBe 0
//     (netEffect.setFlow(_: Flow[Int], _: Reference)(using _: Encoder[Int])).times shouldBe 1
//     (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1
// end MultitierTest
