package io.github.nicolasfara.locix

import io.github.nicolasfara.locix.placement.Peers.Quantifier.*
import io.github.nicolasfara.locix.network.Network.*
import io.github.nicolasfara.locix.placement.PlacedFlow
import io.github.nicolasfara.locix.placement.PlacedFlow.*
import io.github.nicolasfara.locix.placement.PlacedValue
import io.github.nicolasfara.locix.placement.PlacedValue.*
import io.github.nicolasfara.locix.Multitier.*
import ox.flow.Flow
import io.github.nicolasfara.locix.network.InMemoryNetwork

object TweetProcessing:
  type Input <: { type Tie <: Single[Filter] }
  type Filter <: { type Tie <: Single[Mapper] & Single[Input] }
  type Mapper <: { type Tie <: Single[Aggregator] & Single[Filter] }
  type Aggregator <: { type Tie <: Single[Mapper] }

  case class Author(name: String)
  case class Tweet(tags: Set[String], author: Author):
    def hasHashtag(hashtag: String): Boolean = tags.contains(hashtag)

  def pipeline(using Network, PlacedFlow, PlacedValue, Multitier) =
    val tweets = flowOn[Input]:
      Flow.fromValues(
        Tweet(Set("#locix"), Author("Alice")),
        Tweet(Set("#other"), Author("Bob")),
        Tweet(Set("#locix", "#scala"), Author("Charlie")),
        Tweet(Set("#java"), Author("Dave")),
      )
    // Filter tweets with hashtag #locix
    val filtered = flowOn[Filter]:
      val incomingTweets = collectAsLocal[Input, Filter, Tweet](tweets)
      incomingTweets.filter(_.hasHashtag("#locix"))
    // Map author names from filtered tweets
    val mapped = flowOn[Mapper]:
      val incomingTweets = collectAsLocal[Filter, Mapper, Tweet](filtered)
      incomingTweets.map(_.author)
    // Aggregate author names
    val aggregated = on[Aggregator]:
      val incomingAuthors = collectAsLocal[Mapper, Aggregator, Author](mapped)
      val authors = incomingAuthors.runToList().distinct
      println(s"[$localAddress] Authors who tweeted with #locix: ${authors.map(_.name).mkString(", ")}")

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val inputNetwork = InMemoryNetwork[Input]("input-address", 1)
    val filterNetwork = InMemoryNetwork[Filter]("filter-address", 2)
    val mapperNetwork = InMemoryNetwork[Mapper]("mapper-address", 3)
    val aggregatorNetwork = InMemoryNetwork[Aggregator]("aggregator-address", 4)

    inputNetwork.addReachablePeer(filterNetwork)
    filterNetwork.addReachablePeer(inputNetwork)
    filterNetwork.addReachablePeer(mapperNetwork)
    mapperNetwork.addReachablePeer(filterNetwork)
    mapperNetwork.addReachablePeer(aggregatorNetwork)
    aggregatorNetwork.addReachablePeer(mapperNetwork)

    val inputFuture = scala.concurrent.Future:
      println("Starting Input Node")
      given Locix[InMemoryNetwork[Input]] = Locix(inputNetwork)
      PlacedFlow.run[Input]:
        PlacedValue.run[Input]:
          Multitier.run[Input]:
            pipeline
    val filterFuture = scala.concurrent.Future:
      println("Starting Filter Node")
      given Locix[InMemoryNetwork[Filter]] = Locix(filterNetwork)
      PlacedFlow.run[Filter]:
        PlacedValue.run[Filter]:
          Multitier.run[Filter]:
            pipeline
    val mapperFuture = scala.concurrent.Future:
      println("Starting Mapper Node")
      given Locix[InMemoryNetwork[Mapper]] = Locix(mapperNetwork)
      PlacedFlow.run[Mapper]:
        PlacedValue.run[Mapper]:
          Multitier.run[Mapper]:
            pipeline
    val aggregatorFuture = scala.concurrent.Future:
      println("Starting Aggregator Node")
      given Locix[InMemoryNetwork[Aggregator]] = Locix(aggregatorNetwork)
      PlacedFlow.run[Aggregator]:
        PlacedValue.run[Aggregator]:
          Multitier.run[Aggregator]:
            pipeline
    val complete = scala.concurrent.Future.sequence(List(inputFuture, filterFuture, mapperFuture, aggregatorFuture))
    scala.concurrent.Await.result(complete, scala.concurrent.duration.Duration.Inf)
  end main
end TweetProcessing