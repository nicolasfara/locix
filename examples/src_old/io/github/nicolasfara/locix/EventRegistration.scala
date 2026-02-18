package io.github.nicolasfara.locix

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{Locix, Multitier}

import Multitier.*
import network.Network.*
import placement.Peers.Quantifier.*
import placement.Peers.peer
import placement.PlacedValue
import placement.PlacedValue.*
import placement.PlacementType.on
import java.util.UUID

object EventRegistration:
  // Peer type definitions
  type Smartphone <: { type Tie <: Single[Cloud] }
  type Cloud <: { type Tie <: Multiple[Smartphone] }

  final case class RegistrationForm(userId: String, timestamp: Long)

  def registerUser(form: RegistrationForm): UUID =
    println(s"[Cloud] Registered user ${form.userId} at ${form.timestamp}")
    UUID.randomUUID()

  def registration(userId: String)(using Network, PlacedValue, Multitier) =
    val registrationRequest = on[Smartphone] { RegistrationForm(userId, System.currentTimeMillis()) }
    val ids = on[Cloud]:
      val request = asLocalAll(registrationRequest)
      request.view.mapValues(registerUser).toMap
    on[Smartphone]:
      val localId = getId(localAddress)
      val registrationId = asLocal(ids)(localId)
      println(s"[Smartphone $localAddress] Received registration ID: $registrationId")

end EventRegistration
