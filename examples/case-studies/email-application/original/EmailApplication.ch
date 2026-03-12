class EmailSystem@(Client, Server) {
  private DiChannel@(Client, Server) chClientServer = ...;
  private DiChannel@(Server, Client) chServerClient = ...;

  private MailServerDB@Server serverDB = ...;
  private MailDB@Client clientDB = ...;

  Unit@Client updateEmails(UserId@Client userId) {
    contextAwareUpdate(getEmails(userId, clientDB.lastCheckOut()));
  }

  List@Client<Email> getEmails(UserId@Client id, Timestamp@Client ts) {
    return chServerClient.com(
      serverDB.since(
        chClientServer.com(id),
        chClientServer.com(ts)
      )
    );
  }

  Unit@Client contextAwareUpdate(List@Client<Email> emails) {
    clientDB.update(emails);
    if (ClientLib@Client.isOnFlatRate()) {
      chClientServer.select(Choice@Client.THEN);
      clientDB.updateAttachments(
        chServerClient.com(
          serverDB.getAttachments(
            chClientServer.com(clientDB.extractIds(emails))
          )
        )
      );
    } else {
      chClientServer.select(Choice@Client.ELSE);
    }
  }
}