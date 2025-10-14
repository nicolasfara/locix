package io.github.nicolasfara.locicope.placement

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger

class FlowTest extends AsyncFlatSpec with Matchers:

  "Flow" should "support creating flows from iterables" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5))
    flow.runToList().map(_ shouldBe List(1, 2, 3, 4, 5))

  it should "support map transformations" in:
    val flow = Flow.fromIterable(List(1, 2, 3)).map(_ * 2)
    flow.runToList().map(_ shouldBe List(2, 4, 6))

  it should "support filter operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).filter(_ % 2 == 0)
    flow.runToList().map(_ shouldBe List(2, 4))

  it should "support flatMap operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3)).flatMap(x => Flow.fromIterable(List(x, x)))
    flow.runToList().map(_ shouldBe List(1, 1, 2, 2, 3, 3))

  it should "support take operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).take(3)
    flow.runToList().map(_ shouldBe List(1, 2, 3))

  it should "support drop operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).drop(2)
    flow.runToList().map(_ shouldBe List(3, 4, 5))

  it should "support collect operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).collect:
      case x if x % 2 == 0 => x * 10
    flow.runToList().map(_ shouldBe List(20, 40))

  it should "support concat operations" in:
    val flow1 = Flow.fromIterable(List(1, 2, 3))
    val flow2 = Flow.fromIterable(List(4, 5, 6))
    val concatenated = flow1.concat(flow2)
    concatenated.runToList().map(_ shouldBe List(1, 2, 3, 4, 5, 6))

  it should "support fold operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5))
    flow.fold(0)(_ + _).map(_ shouldBe 15)

  it should "support reduce operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5))
    flow.reduce(_ + _).map(_ shouldBe 15)

  it should "support subscription with callbacks" in:
    val flow = Flow.fromIterable(List(1, 2, 3))
    val results = collection.mutable.ListBuffer[Int]()
    val completedCount = new AtomicInteger(0)
    val promise = Promise[Unit]()
    
    val subscription = flow.subscribe(
      onNext = results += _,
      onComplete = () => {
        completedCount.incrementAndGet()
        promise.success(())
      }
    )
    
    promise.future.map { _ =>
      subscription.cancel()
      results.toList shouldBe List(1, 2, 3)
      completedCount.get() shouldBe 1
    }

  it should "support creating single-value flows" in:
    val flow = Flow.single(42)
    flow.runToList().map(_ shouldBe List(42))

  it should "support empty flows" in:
    val flow = Flow.empty[Int]
    flow.runToList().map(_ shouldBe List.empty)

  it should "support foreach operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3))
    val results = collection.mutable.ListBuffer[Int]()
    val finalFlow = flow.foreach(results += _)
    finalFlow.runToList().map { _ =>
      results.toList shouldBe List(1, 2, 3)
    }

  it should "support takeWhile operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).takeWhile(_ < 4)
    flow.runToList().map(_ shouldBe List(1, 2, 3))

  it should "support dropWhile operations" in:
    val flow = Flow.fromIterable(List(1, 2, 3, 4, 5)).dropWhile(_ < 3)
    flow.runToList().map(_ shouldBe List(3, 4, 5))

  it should "handle futures as flows" in:
    import scala.concurrent.Future
    val futureFlow = Flow.fromFuture(Future.successful(42))
    futureFlow.runToList().map(_ shouldBe List(42))

  it should "support unfold operations" in:
    val flow = Flow.unfold(0)(n => if n < 5 then Some((n, n + 1)) else None)
    flow.runToList().map(_ shouldBe List(0, 1, 2, 3, 4))

  it should "chain multiple operations" in:
    val flow = Flow.fromIterable(1 to 10)
      .filter(_ % 2 == 0)
      .map(_ * 2)
      .take(3)
    flow.runToList().map(_ shouldBe List(4, 8, 12))
end FlowTest