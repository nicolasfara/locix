package io.github.nicolasfara.locicope

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import io.github.nicolasfara.locicope.Net.Net
import io.github.nicolasfara.locicope.Multitier.Multitier
import io.github.nicolasfara.locicope.utils.TestCodec.given
import io.github.nicolasfara.locicope.Multitier.placed
import io.github.nicolasfara.locicope.utils.ClientServerArch.{Client, Server }

class MultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[Net.Effect]
  given net: Locicope[Net.Effect](netEffect)

  before:
    resetStubs()

  "The Multitier capability" should "access a remote value" in:
    def multitierSingleValue(using Net, Multitier) = 
      val valueOnServer = placed[Server](10)
      placed[Client]:
        val localValue = valueOnServer.asLocal
        localValue shouldBe 10
        localValue

    Multitier.run[Client](multitierSingleValue)
