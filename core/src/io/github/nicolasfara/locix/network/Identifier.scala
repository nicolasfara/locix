package io.github.nicolasfara.locix.network

trait Identifier:
  val id: String
  val namespace: Option[String] = None
  val metadata: Map[String, String] = Map.empty
