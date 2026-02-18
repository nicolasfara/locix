package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.*
import io.github.nicolasfara.locix.Choreography
import io.github.nicolasfara.locix.Choreography.*
import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.Multitier.*
import java.util.UUID
import io.github.nicolasfara.locix.placement.PlacedFlow.*
import ox.flow.Flow

object RecommendationSystem:
  type Smartphone <: { type Tie <: Single[Cloud] }
  type Cloud <: { type Tie <: Multiple[Smartphone] & Single[Edge] }
  type Edge <: { type Tie <: Multiple[Smartphone] & Single[Cloud] }

  private val registeredUsers = Map[UUID, String]()

  def recommendContent(using Network, PlacedValue, PlacedFlow, Choreography, Multitier)(userId: UUID) =
    val reccomendationRequest = on[Smartphone] { userId }
    val edgeUserReq = on[Edge] { asLocalAll(reccomendationRequest).values }
    val reqOnCloud = comm[Edge, Cloud](edgeUserReq)
    val userAuth = on[Cloud]:
      reqOnCloud.take.map(id => id -> registeredUsers.get(id)).collect {
        case (id, Some(name)) =>
          println(s"[Cloud] Authenticated user $name with ID $id")
          id -> name
      }.toMap
    val recommendations = flowOn[Edge]:
      val authReq = asLocal(userAuth).map { case (id, name) =>
        println(s"[Edge] Generating recommendations for user $name with ID $id")
        id -> s"Recommended content for $name"
      }
      Flow.fromIterable(authReq)
    on[Smartphone]:
      recommendations.takeFlow.filter(_._1 == userId).runForeach { case (id, rec) =>
        println(s"[Smartphone $localAddress] Received recommendation for user ID $id: $rec")
      }
    