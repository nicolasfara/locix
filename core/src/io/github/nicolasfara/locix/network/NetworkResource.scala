package io.github.nicolasfara.locix.network

import io.github.nicolasfara.locix.placement.Peers.PeerRepr
import io.github.nicolasfara.locix.placement.Peers.Peer

object NetworkResource:
  /**
   * Type representing the value type of placed values.
   */
  enum ValueType:
    case Flow
    case Value

  /**
   * Identifier for a resource in the multitier application.
   *
   * @param resourceId
   *   the name of the peer where the resource is located.
   * @param valueType
   *   description of the value type this resource holds, either a flow or a simple value.
   */
  final case class Reference[-P <: Peer](resourceId: String, onPeer: PeerRepr[P], valueType: ValueType):
    override def toString: String = s"$resourceId@$onPeer:${valueType.toString}"
