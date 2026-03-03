package batch.tokenring

import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import loci.serializer.upickle._

import rescala.default._

import java.util.UUID


@multitier object TokenRing {
  type Id = UUID
  type Token = String

  @peer type Prev <: { type Tie <: Single[Prev] }
  @peer type Next <: { type Tie <: Single[Next] }
  @peer type Node <: Prev with Next { type Tie <: Single[Prev] with Single[Next] }

  val id: Id on Prev = UUID.randomUUID

  val sendToken: Local[Evt[(Id, Token)]] on Prev = Evt[(Id, Token)]()

  val recv: Local[Event[Token]] on Prev =
    sent.asLocal collect {
      case (receiver, token) if receiver == id => token
    }

  val sent: Event[(Id, Token)] on Prev =
    (sent.asLocal \ recv) || sendToken

  def main() = on[Node] {
    recv observe { token =>
      println(s"""received:  "$token"\n      on:             $id""")
      multitier.terminate()
    }

    sendToken.fire(id -> s"token for $id")
  }
}


object TokenRingMain extends App {
  val ports = 1095 to 1099

  val Seq(first, middle @ _*) = ports zip ports.tail: @unchecked
  val last = (ports.last, ports.head)

  val requestors =
    (TCP(first._1).firstConnection -> TCP(first._2).firstConnection) +:
    (middle map { middle =>
      TCP("localhost", middle._1) -> TCP(middle._2).firstConnection }) :+
    (TCP("localhost", last._1) -> TCP("localhost", last._2))

  requestors foreach { requestor =>
    multitier start new Instance[TokenRing.Node](
      connect[TokenRing.Prev] { requestor._1 } and
      connect[TokenRing.Next] { requestor._2 })
  }
}
