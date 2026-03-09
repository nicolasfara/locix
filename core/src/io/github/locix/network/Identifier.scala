package io.github.locix.network

case class Identifier(val id: String, val namespace: Option[String] = None, val metadata: Map[String, String] = Map.empty)
