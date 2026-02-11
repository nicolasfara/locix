package io.github.nicolasfara.locix

import scala.caps.ExclusiveCapability
import scala.caps.SharedCapability
import scala.compiletime.Erased

trait Scope[L] // extends ExclusiveCapability

trait Multiparty extends SharedCapability
