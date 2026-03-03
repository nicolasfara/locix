package interactive.chatsimple

import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import loci.serializer.upickle._

import rescala.default._


@multitier object Chat {
  @peer type Server <: { type Tie <: Multiple[Client] }
  @peer type Client <: { type Tie <: Single[Server] }

  val message = on[Client] { Evt[String]() }

  val publicMessage = on[Server] sbj { client: Remote[Client] =>
    message.asLocalFromAllSeq collect {
      case (remote, message) if remote != client => message
    }
  }

  def main() = on[Client] {
    publicMessage.asLocal observe println

    for (line <- scala.io.Source.stdin.getLines())
      message.fire(line)
  }
}


object Server extends App {
  multitier start new Instance[Chat.Server](
    listen[Chat.Client] { TCP(43053) })
}

object Client extends App {
  multitier start new Instance[Chat.Client](
    connect[Chat.Server] { TCP("localhost", 43053) })
}
