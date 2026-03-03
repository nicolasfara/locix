package interactive.timeservice

import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import loci.serializer.upickle._

import rescala.default._

import scala.io
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat


@multitier object TimeService {
  @peer type Peer
  @peer type Server <: Peer { type Tie <: Multiple[Client] }
  @peer type Client <: Peer { type Tie <: Single[Server] }

  val time = on[Server] { Var(0L) }


  def main() =
    (on[Server] {
      while (true) {
        time.set(Calendar.getInstance.getTimeInMillis)
        Thread.sleep(1000)
      }
    }) and
    (on[Client] {
      val format = Var(new SimpleDateFormat("h:m:s"))

      val display = Signal { format().format(new Date(time.asLocal())) }

      val show = Evt[Unit]()

      show map { _ => display() } observe println

      println("type `show` or specify a time format, e.g., `h:m:s`")

      while (multitier.running)
        io.StdIn.readLine() match {
          case "show" =>
            show.fire()
          case "exit" =>
            multitier.terminate()
          case line =>
            try format.set(new SimpleDateFormat(line))
            catch { case e: IllegalArgumentException => println(e.getMessage) }
        }
    })
}


object Server extends App {
  multitier start new Instance[TimeService.Server](
    listen[TimeService.Client] { TCP(43053) })
}

object Client extends App {
  multitier start new Instance[TimeService.Client](
    connect[TimeService.Server] { TCP("localhost", 43053) })
}
