package io.github.nicolasfara.locix

import scala.caps.ExclusiveCapability
import scala.caps.SharedCapability

trait Multiparty extends ExclusiveCapability:
  trait Scope[L <: Multiparty]