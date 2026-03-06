@multitier object EmailSystem {
  @peer type Client <: { type Tie <: Single[Server] }
  @peer type Server <: { type Tie <: Single[Client] }

  private val serverDB: MailServerDB on Server = ...
  private val clientDB: MailDB on Client = ...

  def updateEmails(userId: UserId): Unit on Client = on[Client] {
    val timestamp: Timestamp = clientDB.latestCheckout
    val emails: List[Email] = on[Server].run.capture(userId, timestamp) {
      serverDB.since(userId, timestamp)
    }.asLocal

    clientDB.update(emails)

    if (ClientLib.isOnFlatRate) {
      val ids = clientDB.extractIds(emails)
      clientDB.updateAttachments(
        on[Server].run.capture(ids) { serverDB.getAttachments(ids) }.asLocal)
    }
  }
}
