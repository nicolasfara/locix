package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.placement.PlacedValue.{PlacedValue, on}
import io.github.nicolasfara.locix.network.Network.Network
import io.github.nicolasfara.locix.Multitier.Multitier
import io.github.nicolasfara.locix.placement.PlacedValue.take
import io.github.nicolasfara.locix.Multitier.asLocal

object EmailSystem:
  type Client <: { type Tie <: Single[Server] }
  type Server <: { type Tie <: Single[Client] }

  type UserId = String

  trait MainServerDB:
    def getAttachments(emailId: Long): List[Attachment]
    def since(id: UserId, ts: Long): List[Email]

  trait MainDB:
    def updateAttachments(attachments: List[Attachment]): Unit
    def update(emails: List[Email]): Unit
    def extractIds(emails: List[Email]): List[Long] =
      emails.map(_.id)
    def lastCheckout: Long

  case class Email(id: Long, subject: String, body: String, timestamp: Long, attachments: List[Attachment])
  case class Attachment(filename: String, data: Array[Byte])
  object ClientLib:
    val isOnFlatRate = true

  val serverDB: (Network, PlacedValue) ?=> MainServerDB on Server = on[Server](???)
  val clientDB: (Network, PlacedValue) ?=> MainDB on Client = on[Client](???)

  def updateEmails(userId: UserId)(using Network, PlacedValue, Multitier): Unit on Client = on[Client]:
    contextAwareUpdate(getEmails(userId, clientDB.take.lastCheckout).take)

  def getEmails(userId: UserId, ts: Long)(using Network, PlacedValue, Multitier): List[Email] on Client =
    val emailsOnServer = on[Server]:
      val sdb = serverDB.take
      sdb.since(userId, ts)
    on[Client](asLocal(emailsOnServer))

  def contextAwareUpdate(emails: List[Email])(using Network, PlacedValue, Multitier): Unit on Client = on[Client]:
    val db = clientDB.take
    db.update(emails)
    if ClientLib.isOnFlatRate then
      val emailIds = db.extractIds(emails)
      updateAttachments(emailIds)
    else ()

  def updateAttachments(emailIds: List[Long])(using Network, PlacedValue, Multitier): Unit on Client =
    val attachmentsOnServer = on[Server]:
      val sdb = serverDB.take
      emailIds.flatMap(sdb.getAttachments)
    on[Client]:
      val attachments = asLocal(attachmentsOnServer)
      clientDB.take.updateAttachments(attachments)
end EmailSystem