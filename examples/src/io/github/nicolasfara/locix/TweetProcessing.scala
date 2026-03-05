package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.peers.Peers.Cardinality.*
import io.github.nicolasfara.locix.network.Network
import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.placement.Placement
import io.github.nicolasfara.locix.signal.Signal.signalBuilder
import io.github.nicolasfara.locix.placement.PlacementType.*
import io.github.nicolasfara.locix.Multitier.*
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.nicolasfara.locix.signal.Signal
import io.github.nicolasfara.locix.peers.Peers.Peer
import io.github.nicolasfara.locix.peers.Peers.PeerTag
import io.github.nicolasfara.locix.placement.PlacementType
import io.github.nicolasfara.locix.raise.Raise
import io.github.nicolasfara.locix.network.NetworkError
import io.github.nicolasfara.locix.handlers.PlacementTypeHandler
import io.github.nicolasfara.locix.handlers.MultitierHandler
import io.github.nicolasfara.locix.distributed.InMemoryNetwork
import scala.concurrent.Future
import scala.concurrent.Await

object TweetProcessing:
  type Input <: { type Tie <: Single[Filter] }
  type Filter <: { type Tie <: Single[Mapper] & Single[Input] }
  type Mapper <: { type Tie <: Single[Aggregator] & Single[Filter] }
  type Aggregator <: { type Tie <: Single[Mapper] }

  case class Author(name: String)
  case class Tweet(tags: Set[String], author: Author):
    def hasHashtag(hashtag: String): Boolean = tags.contains(hashtag)

  def processTweetsPipeline(using Network, Placement, Multitier) = Multitier:
    val tweets = on[Input]:
      signalBuilder[Tweet]: emitter =>
        Thread.sleep(100) // Simulate delay between tweets
        emitter.emit(Tweet(Set("#locix"), Author("Alice")))
        Thread.sleep(100) // Simulate delay between tweets
        emitter.emit(Tweet(Set("#other"), Author("Bob")))
        Thread.sleep(100) // Simulate delay between tweets
        emitter.emit(Tweet(Set("#locix", "#scala"), Author("Charlie")))
        Thread.sleep(100) // Simulate delay between tweets
        emitter.emit(Tweet(Set("#java"), Author("Dave")))
    val filtered = on[Filter] { asLocal[Filter, Input](tweets).filter(_.hasHashtag("#locix")) }
    val mapped = on[Mapper] { asLocal[Mapper, Filter](filtered).map(_.author) }
    val aggregated = on[Aggregator]:
      asLocal(mapped).subscribe: author =>
        println(s"[$peerAddress] Author who tweeted with #locix: ${author.name}")
      Thread.sleep(500) // Wait to ensure all authors are printed before the program exits

  private def handleProgramForPeer[P <: Peer: PeerTag](net: Network)[V](program: (Network, PlacementType, Multitier) ?=> V): V =
    given Network = net
    given Raise[NetworkError] = Raise.rethrowError
    given ptHandler: PlacementType = PlacementTypeHandler.handler[P]
    given cHandler: Multitier = MultitierHandler.handler[P]
    program

  def main(args: Array[String]): Unit =
    println("Running TweetProcessing pipeline...")
    val broker = InMemoryNetwork.broker()
    val inputNetwork = InMemoryNetwork[Input]("input-address", broker)
    val filterNetwork = InMemoryNetwork[Filter]("filter-address", broker)
    val mapperNetwork = InMemoryNetwork[Mapper]("mapper-address", broker)
    val aggregatorNetwork = InMemoryNetwork[Aggregator]("aggregator-address", broker)

    val brokerFuture = Future { handleProgramForPeer[Input](inputNetwork)(processTweetsPipeline) }
    val filterFuture = Future { handleProgramForPeer[Filter](filterNetwork)(processTweetsPipeline) }
    val mapperFuture = Future { handleProgramForPeer[Mapper](mapperNetwork)(processTweetsPipeline) }
    val aggregatorFuture = Future { handleProgramForPeer[Aggregator](aggregatorNetwork)(processTweetsPipeline) }

    // Wait for all peers to finish
    val combinedFuture = Future.sequence(Seq(brokerFuture, filterFuture, mapperFuture, aggregatorFuture))
    Await.result(combinedFuture, scala.concurrent.duration.Duration.Inf)
end TweetProcessing
