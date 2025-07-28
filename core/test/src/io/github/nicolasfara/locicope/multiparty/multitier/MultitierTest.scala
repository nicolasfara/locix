package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.placement.PlacementType.{on, given}
import io.github.nicolasfara.locicope.utils.{InMemoryNetwork, PlacementUtils, TestCodec}
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.multiparty.multitier.Multitier.*
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.utils.TestCodec.given

class MultitierTest extends AnyFlatSpecLike, Matchers, Inside:
  "The Multitier capability" should "access single remote value locally" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*

    given InMemoryNetwork()

    val valueOnServer = PlacementUtils.remotePlacement[Int, Server](10)
    multitier[Client]:
      placed[Client]:
        val localValue = valueOnServer.asLocal
        localValue shouldBe 10
        localValue
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
end MultitierTest
