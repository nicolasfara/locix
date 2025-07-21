package io.github.nicolasfara.locicope.multiparty.multitier

import io.github.nicolasfara.locicope.placement.PlacementType.{ on, given }
import io.github.nicolasfara.locicope.utils.{ InMemoryNetwork, Placement }
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.multiparty.multitier.Multitier.*
import io.github.nicolasfara.locicope.utils.TestCodec.given

class MultitierTest extends AnyFlatSpecLike, Matchers, Inside:
  "The Multitier capability" should "access single remote value locally" in:
    import io.github.nicolasfara.locicope.utils.ClientServerArch.*

    given InMemoryNetwork()

    val valueOnServer = Placement.remotePlacement[Int, Server](10)
    multitier[Client]:
      placed[Client]:
        val localValue = valueOnServer.asLocal
        localValue shouldBe 10
        localValue
