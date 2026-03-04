package io.github.nicolasfara.locix

import java.util.concurrent.CountDownLatch
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.github.nicolasfara.locix.Multitier
import io.github.nicolasfara.locix.Multitier.*
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import io.github.nicolasfara.locix.handlers.MultitierHandler
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Network.peerAddress
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.placement.PlacementType.on
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.signal.Signal
import io.github.nicolasfara.locix.signal.Signal.signalBuilder

object BasicChat:
  type Server <: { type Tie <: Multiple[Client] }
  type Client <: { type Tie <: Single[Server] }

  final case class Entry(name: String, msg: String)

  private val scripts: Map[String, List[String]] = Map(
    "alice" -> List("Hello everyone.", "I am exploring Locix."),
    "bob"   -> List("Bob joined the room.", "Nice to meet you all."),
    "carol" -> List("Carol says hi.", "Broadcasts look correct."),
  )

  private val startupDelayMs = 500L
  private val betweenMessagesDelayMs = 150L

  def basicChatProtocol(using Network, Placement, Multitier): Unit = Multitier:
    val submissionsOnClient = on[Client]:
      val clientId = peerAddress.asInstanceOf[String]
      val messages = scripts.getOrElse(clientId, List(s"Hello from $clientId"))
      signalBuilder[Entry]: emitter =>
        Thread.sleep(startupDelayMs)
        messages.foreach: text =>
          println(s"[Client $clientId] submit '$text'")
          emitter.emit(Entry(clientId, text))
          Thread.sleep(betweenMessagesDelayMs)

    val broadcastOnServer = on[Server]:
      val submissionStreams = asLocalAll[Server, Client](submissionsOnClient).toMap
      println(s"[Server] Tracking ${submissionStreams.size} client stream(s).")

      val mergedSubmissions = Signal.merge(submissionStreams.values.toSeq)
      val (broadcastSignal, broadcastEmitter) = Signal.make[Entry]
      val allStreamsClosed = CountDownLatch(submissionStreams.size)

      submissionStreams.toSeq.sortBy(_._1.toString).foreach:
        case (clientId, stream) =>
          stream.onClose: () =>
            allStreamsClosed.countDown()
            println(s"[Server] Stream closed for $clientId (${allStreamsClosed.getCount} remaining).")

      mergedSubmissions.subscribe: entry =>
        println(s"[Server] broadcasting '${entry.name}: ${entry.msg}'")
        broadcastEmitter.emit(entry)

      Future:
        allStreamsClosed.await()
        println("[Server] All submission streams closed. Closing broadcast stream.")
        broadcastEmitter.close()

      broadcastSignal

    on[Client]:
      val clientId = peerAddress.asInstanceOf[String]
      val incomingBroadcast = asLocal[Client, Server](broadcastOnServer)
      val done = CountDownLatch(1)
      val lock = new AnyRef
      var history = List.empty[Entry]

      incomingBroadcast.subscribe: entry =>
        val snapshot = lock.synchronized:
          history = entry :: history
          history
        val timeline = snapshot.map(e => s"${e.name}: ${e.msg}").mkString(" | ")
        println(s"[Client $clientId] timeline -> $timeline")

      incomingBroadcast.onClose: () =>
        println(s"[Client $clientId] broadcast stream closed.")
        done.countDown()

      done.await()
  end basicChatProtocol

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](
      program: (Network, PlacementType, Multitier) ?=> V,
  ): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given mtHandler: Multitier = MultitierHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running BasicChat scripted port...")
    val broker = InMemoryNetwork.broker()
    val serverNetwork = InMemoryNetwork[Server]("server", broker)
    val aliceNetwork = InMemoryNetwork[Client]("alice", broker)
    val bobNetwork = InMemoryNetwork[Client]("bob", broker)
    val carolNetwork = InMemoryNetwork[Client]("carol", broker)

    val serverFuture = Future { handleProgramForPeer[Server](serverNetwork)(basicChatProtocol) }
    val aliceFuture = Future { handleProgramForPeer[Client](aliceNetwork)(basicChatProtocol) }
    val bobFuture = Future { handleProgramForPeer[Client](bobNetwork)(basicChatProtocol) }
    val carolFuture = Future { handleProgramForPeer[Client](carolNetwork)(basicChatProtocol) }

    val all = Future.sequence(Seq(serverFuture, aliceFuture, bobFuture, carolFuture))
    Await.result(all, scala.concurrent.duration.Duration.Inf)
    println("BasicChat done.")
end BasicChat
