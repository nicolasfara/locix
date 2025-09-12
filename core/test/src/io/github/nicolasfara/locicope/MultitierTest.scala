package io.github.nicolasfara.locicope

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalamock.stubs.Stubs
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers

class MultitierTest extends AnyFlatSpecLike, Matchers, Stubs, BeforeAndAfter:

  private val netEffect = stub[Net.Effect]
  given net: Locicope[Net.Effect](netEffect)

  before:
    resetStubs()

