package batch.pipeline

import loci.language._
import loci.language.transmitter.rescala._
import loci.communicator.tcp._
import loci.serializer.upickle._
import loci.transmitter.IdenticallyTransmittable

import rescala.default._

import upickle.default._


case class Author(name: String)

object Author {
  implicit val authorTransmittable: IdenticallyTransmittable[Author] = IdenticallyTransmittable()
  implicit val authorSerializer: ReadWriter[Author] = macroRW[Author]
}

case class Tweet(tags: Set[String], author: Author) {
  def hasHashtag(tag: String) = tags contains tag
}

object Tweet {
  implicit val tweetTransmittable: IdenticallyTransmittable[Tweet] = IdenticallyTransmittable()
  implicit val tweetSerializer: ReadWriter[Tweet] = macroRW[Tweet]
}

@multitier object Pipeline {
  @peer type Peer

  @peer type Input <: Peer { type Tie <: Single[Filter] }
  @peer type Filter <: Peer { type Tie <: Single[Mapper] with Single[Input] }
  @peer type Mapper <: Peer { type Tie <: Single[Folder] with Single[Filter] } 
  @peer type Folder <: Peer { type Tie <: Single[Mapper] }

  val tweetStream: Evt[Tweet] on Input = Evt[Tweet]()

  val filtered: Event[Tweet] on Filter =
    tweetStream.asLocal filter { tweet => tweet.hasHashtag("multitier") }

  val mapped: Event[Author] on Mapper =
    filtered.asLocal map { tweet => tweet.author }

  val folded: Signal[Map[Author, Int]] on Folder =
    mapped.asLocal.fold(Map.empty[Author, Int].withDefaultValue(0)) {
      (map, author) => map + (author -> (map(author) + 1))
    }

  def main(): Unit on Peer =
    (on[Input] {
      tweetStream.fire(Tweet(Set("distributed", "multitier"), Author("Some author")))
      tweetStream.fire(Tweet(Set("distributed", "reactive"), Author("Some other author")))
      tweetStream.fire(Tweet(Set("distributed", "multitier", "reactive"), Author("Some author")))
    }) and
    (on[Folder] {
      var count = 0
      folded observe { result =>
        println(s"result: ${
          if (result.isEmpty)
            "(none)"
          else
            result map { case (author, count) => s"${author.name} ($count)" } mkString ","
        }")

        count += 1
        if (count > 2)
          multitier.terminate()
      }
    })
}


object PipelineMain extends App {
  multitier start new Instance[Pipeline.Folder](
    connect[Pipeline.Mapper] { TCP(1095).firstConnection })

  multitier start new Instance[Pipeline.Mapper](
    connect[Pipeline.Folder] { TCP("localhost", 1095) } and
    connect[Pipeline.Filter] { TCP(1096).firstConnection })

  multitier start new Instance[Pipeline.Filter](
    connect[Pipeline.Mapper] { TCP("localhost", 1096) } and
    connect[Pipeline.Input] { TCP(1097).firstConnection })

  multitier start new Instance[Pipeline.Input](
    connect[Pipeline.Filter] { TCP("localhost", 1097) })
}
