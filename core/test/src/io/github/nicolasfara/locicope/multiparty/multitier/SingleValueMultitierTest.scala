package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.placement.PlacementType.{on, given}
import io.github.nicolasfara.locicope.utils.{InMemoryNetwork, PlacementUtils, TestCodec}
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.multiparty.multitier.Multitier.*
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.utils.TestCodec.given
import ox.flow.Flow

class SingleValueMultitierTest extends AnyFlatSpecLike, Matchers, Inside:
  "The Multitier capability" should "access single remote value locally" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*
    given InMemoryNetwork()

    val valueOnServer = PlacementUtils.remotePlacement[Int, Server](10)
    multitier[Client]:
      placed[Client]:
        val localValue = valueOnServer.asLocal
        localValue shouldBe 10
        localValue
  it should "access a flow on a remote peer" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*
    given InMemoryNetwork()

    val flowOnServer = PlacementUtils.remoteFlowPlacement[Int, Server](Flow.fromIterable(Seq(1, 2, 3)))
    multitier[Client]:
      placed[Client]:
        val localFlow = flowOnServer.asLocal
        val values = localFlow.runToList()
        values shouldBe List(1, 2, 3)
        values.sum
  it should "access single local value" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*
    given InMemoryNetwork()

    val valueOnClient = PlacementUtils.localPlacement[Int, Client](20)
    multitier[Client]:
      placed[Client]:
        val localValue = valueOnClient.unwrap
        localValue shouldBe 20
        localValue
  it should "access a flow on a local peer" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*
    given InMemoryNetwork()
    
    val flowOnClient = PlacementUtils.localFlowPlacement[Int, Client](Flow.fromIterable(Seq(4, 5, 6)))
    multitier[Client]:
      placed[Client]:
        val localFlow = flowOnClient.unwrap
        val values = localFlow.runToList()
        values shouldBe List(4, 5, 6)
        values.sum
  it should "call a placed function on a local peer" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*
    given net: InMemoryNetwork()
    
    def placedOnServer(using Network, Multitier) = function[(Int, Int), Int, on, Client]:
      case (x: Int, y: Int) => x + y
    
    multitier[Client]:
      net.registerFunction(placedOnServer)
      placed[Client]:
        val result = placedOnServer((10, 10)).unwrap
        result shouldBe 20
        ()
end SingleValueMultitierTest
