// package io.github.nicolasfara.locicope

// import io.github.nicolasfara.locicope.Choreography.Choreography
// import io.github.nicolasfara.locicope.Net.Net
// import io.github.nicolasfara.locicope.PlacementType.{ on, unwrap }
// import io.github.nicolasfara.locicope.network.NetworkResource.Reference
// import io.github.nicolasfara.locicope.serialization.{ Decoder, Encoder }
// import io.github.nicolasfara.locicope.utils.ClientServerArch.{ Client, Server }
// import org.scalamock.stubs.Stubs
// import org.scalatest.BeforeAndAfter
// import org.scalatest.flatspec.AnyFlatSpecLike
// import org.scalatest.matchers.should.Matchers
// import io.github.nicolasfara.locicope.utils.TestCodec.given

// class ChoreographyTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:
//   private val netEffect = stub[Net.Effect]
//   given net: Locicope[Net.Effect](netEffect)

//   before:
//     resetStubs()

//   "A choreography program" should "allow retrieving through the network a remote value after communication" in:
//     (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).returnsWith(())
//     // Simulate two clients sending the value [0, 1]
//     (netEffect.getValues(_: Reference)(using _: Decoder[Int])).returnsWith(Right(Map(0 -> 10, 1 -> 11)))
//     def choreographyProgram(using Net, Choreography): Unit =
//       val foo: Int on Client = Choreography.at[Client](10)
//       val fooOnServer: Int on Server = Choreography.comm(foo)
//       Choreography.at[Server]:
//         val localValue: Map[Int, Int] = fooOnServer.unwrapAll
//         localValue shouldBe Map(0 -> 10, 1 -> 11) // Multiple clients send the value
//         localValue
//     // Run the choreography program from the server side
//     Choreography.run[Server](choreographyProgram)
//     (netEffect.setValue(_: Int, _: Reference)(using _: Encoder[Int])).times shouldBe 1 // Register value on `at[Server]`
//     (netEffect.getValues(_: Reference)(using _: Decoder[Int])).times shouldBe 1 // Retrieve value on `comm`
// end ChoreographyTest
