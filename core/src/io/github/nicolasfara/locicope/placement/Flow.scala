package io.github.nicolasfara.locicope.placement

import scala.concurrent.{ Future, Promise, ExecutionContext }
import scala.util.{ Try, Success, Failure }
import scala.annotation.tailrec
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicReference

/**
 * An asynchronous stream of values.
 * 
 * Provides operations to manipulate and transform the stream.
 * 
 * @tparam V
 *   the type of values in the flow.
 */
trait Flow[+V]:
  
  /**
   * Transform each element of the flow.
   */
  def map[U](f: V => U): Flow[U]
  
  /**
   * Filter elements based on a predicate.
   */
  def filter(predicate: V => Boolean): Flow[V]
  
  /**
   * Transform each element to a flow and flatten the result.
   */
  def flatMap[U](f: V => Flow[U]): Flow[U]
  
  /**
   * Collect elements that match a partial function.
   */
  def collect[U](pf: PartialFunction[V, U]): Flow[U]
  
  /**
   * Take the first n elements.
   */
  def take(n: Int): Flow[V]
  
  /**
   * Skip the first n elements.
   */
  def drop(n: Int): Flow[V]
  
  /**
   * Take elements while predicate is true.
   */
  def takeWhile(predicate: V => Boolean): Flow[V]
  
  /**
   * Skip elements while predicate is true.
   */
  def dropWhile(predicate: V => Boolean): Flow[V]
  
  /**
   * Concatenate with another flow.
   */
  def concat[U >: V](other: Flow[U]): Flow[U]

  /**
   * Merge with another flow.
   */
  def merge[U >: V](other: Flow[U]): Flow[U]
  
  /**
   * Execute a side effect for each element.
   */
  def foreach(f: V => Unit): Flow[V]
  
  /**
   * Fold the flow into a single value asynchronously.
   */
  def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U]
  
  /**
   * Reduce the flow to a single value.
   */
  def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U]
  
  /**
   * Collect all elements into a List.
   */
  def runToList()(using ExecutionContext): Future[List[V]]
  
  /**
   * Subscribe to the flow with a callback.
   */
  def subscribe(onNext: V => Unit, onError: Throwable => Unit = _ => (), onComplete: () => Unit = () => ())(using ExecutionContext): Subscription
  
  /**
   * Convert the flow to an iterator (blocking operation).
   */
  def toIterator(using ExecutionContext): Iterator[V]

/**
 * Subscription handle for flow subscriptions.
 */
trait Subscription:
  def cancel(): Unit
  def isActive: Boolean

object Flow:
  
  /**
   * Create a flow from an iterable.
   */
  def fromIterable[V](iterable: Iterable[V]): Flow[V] = 
    IterableFlow(iterable)
  
  /**
   * Create a flow from a future.
   */
  def fromFuture[V](future: Future[V]): Flow[V] = 
    FutureFlow(future)
  
  /**
   * Create a flow that emits a single value.
   */
  def single[V](value: V): Flow[V] = 
    fromIterable(List(value))
  
  /**
   * Create an empty flow.
   */
  def empty[V]: Flow[V] = 
    fromIterable(List.empty[V])
  
  /**
   * Create a flow from a callback-based source.
   */
  def fromCallback[V](register: (V => Unit, Throwable => Unit, () => Unit) => Subscription): Flow[V] = 
    CallbackFlow(register)
  
  /**
   * Create a flow that repeats a computation.
   */
  def repeat[V](computation: () => V): Flow[V] = 
    RepeatFlow(computation)
  
  /**
   * Create a flow that emits values at regular intervals.
   */
  def interval(millis: Long): Flow[Long] = 
    IntervalFlow(millis)
  
  /**
   * Create a flow that emits an infinite sequence of values.
   */
  def unfold[V, S](seed: S)(f: S => Option[(V, S)]): Flow[V] = 
    UnfoldFlow(seed, f)

  // Internal implementations
  
  private case class IterableFlow[V](iterable: Iterable[V]) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = IterableFlow(iterable.map(f))
    def filter(predicate: V => Boolean): Flow[V] = IterableFlow(iterable.filter(predicate))
    def flatMap[U](f: V => Flow[U]): Flow[U] = 
      ConcatFlow(iterable.map(f).toList)
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = 
      IterableFlow(iterable.collect(pf))
    def take(n: Int): Flow[V] = IterableFlow(iterable.take(n))
    def drop(n: Int): Flow[V] = IterableFlow(iterable.drop(n))
    def takeWhile(predicate: V => Boolean): Flow[V] = IterableFlow(iterable.takeWhile(predicate))
    def dropWhile(predicate: V => Boolean): Flow[V] = IterableFlow(iterable.dropWhile(predicate))
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(f: V => Unit): Flow[V] = 
      IterableFlow(iterable.map { v => f(v); v })
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      Future.successful(iterable.foldLeft(initial)(f))
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      if iterable.isEmpty then Future.failed(new NoSuchElementException("Empty flow"))
      else Future.successful(iterable.reduce(f))
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      Future.successful(iterable.toList)
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val active = new AtomicReference(true)
      val subscription = new Subscription:
        def cancel(): Unit = active.set(false)
        def isActive: Boolean = active.get()
      
      Future:
        try
          for v <- iterable if subscription.isActive do
            onNext(v)
          if subscription.isActive then
            onComplete()
        catch
          case ex: Throwable if subscription.isActive =>
            onError(ex)
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] = iterable.iterator

  private case class FutureFlow[V](future: Future[V]) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = FutureFlow(future.map(f)(using ExecutionContext.global))
    def filter(predicate: V => Boolean): Flow[V] = 
      FutureFlow(future.filter(predicate)(using ExecutionContext.global))
    def flatMap[U](f: V => Flow[U]): Flow[U] = 
      FlatMapFlow(this, f)
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = 
      FutureFlow(future.collect(pf)(using ExecutionContext.global))
    def take(n: Int): Flow[V] = if n <= 0 then Flow.empty else this
    def drop(n: Int): Flow[V] = if n <= 0 then this else Flow.empty
    def takeWhile(predicate: V => Boolean): Flow[V] = filter(predicate)
    def dropWhile(predicate: V => Boolean): Flow[V] = filter(!predicate(_))
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(f: V => Unit): Flow[V] = 
      FutureFlow(future.map { v => f(v); v }(using ExecutionContext.global))
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      future.map(v => f(initial, v))
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      future.asInstanceOf[Future[U]]
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      future.map(List(_))
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val active = new AtomicReference(true)
      val subscription = new Subscription:
        def cancel(): Unit = active.set(false)
        def isActive: Boolean = active.get()
      
      future.onComplete:
        case Success(value) if subscription.isActive =>
          onNext(value)
          onComplete()
        case Failure(ex) if subscription.isActive =>
          onError(ex)
        case _ => // cancelled
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] =
      future.value match
        case Some(Success(value)) => Iterator.single(value)
        case Some(Failure(ex)) => throw ex
        case None => throw new IllegalStateException("Future not completed")

  private case class CallbackFlow[V](register: (V => Unit, Throwable => Unit, () => Unit) => Subscription) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        register(v => onNext(f(v)), onError, onComplete)
      }
    def filter(predicate: V => Boolean): Flow[V] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        register(v => if predicate(v) then onNext(v), onError, onComplete)
      }
    def flatMap[U](f: V => Flow[U]): Flow[U] = FlatMapFlow(this, f)
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        register(v => if pf.isDefinedAt(v) then onNext(pf(v)), onError, onComplete)
      }
    def take(n: Int): Flow[V] = 
      if n <= 0 then Flow.empty
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          var count = 0
          register(
            v => 
              if count < n then
                count += 1
                onNext(v)
                if count == n then onComplete()
            ,
            onError,
            onComplete
          )
        }
    def drop(n: Int): Flow[V] = 
      if n <= 0 then this
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          var count = 0
          register(
            v => 
              count += 1
              if count > n then onNext(v)
            ,
            onError,
            onComplete
          )
        }
    def takeWhile(predicate: V => Boolean): Flow[V] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        register(
          v => 
            if predicate(v) then onNext(v)
            else onComplete()
          ,
          onError,
          onComplete
        )
      }
    def dropWhile(predicate: V => Boolean): Flow[V] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        var dropping = true
        register(
          v => 
            if dropping && predicate(v) then () // drop
            else 
              dropping = false
              onNext(v)
          ,
          onError,
          onComplete
        )
      }
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(f: V => Unit): Flow[V] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        register(v => { f(v); onNext(v) }, onError, onComplete)
      }
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      val promise = Promise[U]()
      var accumulator = initial
      subscribe(
        v => accumulator = f(accumulator, v),
        promise.failure,
        () => promise.success(accumulator)
      )
      promise.future
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      val promise = Promise[U]()
      var accumulator: Option[U] = None
      subscribe(
        v => accumulator = Some(accumulator.fold(v)(f(_, v))),
        promise.failure,
        () => accumulator match
          case Some(value) => promise.success(value)
          case None => promise.failure(new NoSuchElementException("Empty flow"))
      )
      promise.future
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      val promise = Promise[List[V]]()
      val buffer = mutable.ListBuffer[V]()
      subscribe(
        buffer += _,
        promise.failure,
        () => promise.success(buffer.toList)
      )
      promise.future
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      register(onNext, onError, onComplete)
    
    def toIterator(using ExecutionContext): Iterator[V] =
      val buffer = mutable.Queue[V]()
      val promise = Promise[Unit]()
      subscribe(
        buffer += _,
        promise.failure,
        () => promise.success(())
      )
      // This is blocking - not ideal but necessary for iterator interface
      import scala.concurrent.duration._
      import scala.concurrent.Await
      Await.result(promise.future, Duration.Inf)
      buffer.iterator

  private case class ConcatFlow[V](flows: List[Flow[V]]) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = ConcatFlow(flows.map(_.map(f)))
    def filter(predicate: V => Boolean): Flow[V] = ConcatFlow(flows.map(_.filter(predicate)))
    def flatMap[U](f: V => Flow[U]): Flow[U] = ConcatFlow(flows.map(_.flatMap(f)))
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = ConcatFlow(flows.map(_.collect(pf)))
    def take(n: Int): Flow[V] = 
      if n <= 0 then Flow.empty
      else 
        @tailrec
        def loop(remaining: List[Flow[V]], count: Int, acc: List[Flow[V]]): List[Flow[V]] =
          if count <= 0 || remaining.isEmpty then acc.reverse
          else
            val head = remaining.head
            // This is approximate - we don't know how many elements each flow has
            loop(remaining.tail, count - 10, head.take(count) :: acc)
        ConcatFlow(loop(flows, n, Nil))
    def drop(n: Int): Flow[V] = 
      if n <= 0 then this
      else 
        // Simplified approach - drop from first flow
        flows match
          case head :: tail => ConcatFlow(head.drop(n) :: tail)
          case Nil => Flow.empty
    def takeWhile(predicate: V => Boolean): Flow[V] = 
      ConcatFlow(flows.map(_.takeWhile(predicate)))
    def dropWhile(predicate: V => Boolean): Flow[V] = 
      // Simplified - apply to all flows
      ConcatFlow(flows.map(_.dropWhile(predicate)))
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(flows :+ other)
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(flows :+ other)
    def foreach(f: V => Unit): Flow[V] = ConcatFlow(flows.map(_.foreach(f)))
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      flows.foldLeft(Future.successful(initial)) { (futureAcc, flow) =>
        futureAcc.flatMap(acc => flow.fold(acc)(f))
      }
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      if flows.isEmpty then Future.failed(new NoSuchElementException("Empty flow"))
      else
        flows.tail.foldLeft(flows.head.reduce(f)) { (futureAcc, flow) =>
          for
            acc <- futureAcc
            flowReduced <- flow.reduce(f)
          yield f(acc, flowReduced)
        }
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      Future.traverse(flows)(_.runToList()).map(_.flatten)
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val subscriptions = mutable.ListBuffer[Subscription]()
      val active = new AtomicReference(true)
      var completed = 0
      
      val subscription = new Subscription:
        def cancel(): Unit = 
          active.set(false)
          subscriptions.foreach(_.cancel())
        def isActive: Boolean = active.get()
      
      def processFlow(flow: Flow[V]): Unit =
        if subscription.isActive then
          val sub = flow.subscribe(
            onNext,
            onError,
            () => 
              completed += 1
              if completed == flows.length then onComplete()
          )
          subscriptions += sub
      
      flows.foreach(processFlow)
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] =
      flows.flatMap(_.toIterator).iterator

  private case class RepeatFlow[V](computation: () => V) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = RepeatFlow(() => f(computation()))
    def filter(predicate: V => Boolean): Flow[V] = 
      RepeatFlow(() => 
        var value = computation()
        while !predicate(value) do
          value = computation()
        value
      )
    def flatMap[U](f: V => Flow[U]): Flow[U] = FlatMapFlow(this, f)
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = 
      RepeatFlow(() =>
        var value = computation()
        while !pf.isDefinedAt(value) do
          value = computation()
        pf(value)
      )
    def take(n: Int): Flow[V] = 
      if n <= 0 then Flow.empty
      else fromIterable((1 to n).map(_ => computation()))
    def drop(n: Int): Flow[V] = 
      RepeatFlow(() => 
        for _ <- 1 to n do computation() // skip n values
        computation()
      )
    def takeWhile(predicate: V => Boolean): Flow[V] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          try
            var continue = true
            while subscription.isActive && continue do
              val value = computation()
              if predicate(value) then onNext(value)
              else continue = false
            if subscription.isActive then onComplete()
          catch
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def dropWhile(predicate: V => Boolean): Flow[V] = 
      RepeatFlow(() => 
        var value = computation()
        while predicate(value) do
          value = computation()
        value
      )
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(f: V => Unit): Flow[V] = RepeatFlow(() => { val v = computation(); f(v); v })
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      Future.failed(new UnsupportedOperationException("Cannot fold infinite stream"))
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      Future.failed(new UnsupportedOperationException("Cannot reduce infinite stream"))
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      Future.failed(new UnsupportedOperationException("Cannot convert infinite stream to list"))
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val active = new AtomicReference(true)
      val subscription = new Subscription:
        def cancel(): Unit = active.set(false)
        def isActive: Boolean = active.get()
      
      Future:
        try
          while subscription.isActive do
            onNext(computation())
        catch
          case ex: Throwable if subscription.isActive => onError(ex)
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] =
      Iterator.continually(computation())

  private case class IntervalFlow(millis: Long) extends Flow[Long]:
    def map[U](f: Long => U): Flow[U] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          try
            while subscription.isActive do
              onNext(f(count))
              count += 1
              Thread.sleep(millis)
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def filter(predicate: Long => Boolean): Flow[Long] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          try
            while subscription.isActive do
              if predicate(count) then onNext(count)
              count += 1
              Thread.sleep(millis)
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def flatMap[U](f: Long => Flow[U]): Flow[U] = FlatMapFlow(this, f)
    def collect[U](pf: PartialFunction[Long, U]): Flow[U] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          try
            while subscription.isActive do
              if pf.isDefinedAt(count) then onNext(pf(count))
              count += 1
              Thread.sleep(millis)
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def take(n: Int): Flow[Long] = 
      if n <= 0 then Flow.empty
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          val active = new AtomicReference(true)
          val subscription = new Subscription:
            def cancel(): Unit = active.set(false)
            def isActive: Boolean = active.get()
          
          Future {
            var count = 0L
            var emitted = 0
            try
              while subscription.isActive && emitted < n do
                onNext(count)
                emitted += 1
                count += 1
                if emitted < n then Thread.sleep(millis)
              if subscription.isActive then onComplete()
            catch
              case _: InterruptedException => // cancelled
              case ex: Throwable if subscription.isActive => onError(ex)
          }(using ExecutionContext.global)
          subscription
        }
    def drop(n: Int): Flow[Long] = 
      if n <= 0 then this
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          val active = new AtomicReference(true)
          val subscription = new Subscription:
            def cancel(): Unit = active.set(false)
            def isActive: Boolean = active.get()
          
          Future {
            var count = 0L
            try
              while subscription.isActive do
                if count >= n then onNext(count)
                count += 1
                Thread.sleep(millis)
            catch
              case _: InterruptedException => // cancelled
              case ex: Throwable if subscription.isActive => onError(ex)
          }(using ExecutionContext.global)
          subscription
        }
    def takeWhile(predicate: Long => Boolean): Flow[Long] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          try
            var continue = true
            while subscription.isActive && continue do
              if predicate(count) then 
                onNext(count)
                count += 1
                Thread.sleep(millis)
              else 
                continue = false
            if subscription.isActive then onComplete()
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def dropWhile(predicate: Long => Boolean): Flow[Long] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          var dropping = true
          try
            while subscription.isActive do
              if dropping && predicate(count) then 
                // skip
                count += 1
                Thread.sleep(millis)
              else 
                dropping = false
                onNext(count)
                count += 1
                Thread.sleep(millis)
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    def concat[U >: Long](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: Long](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(f: Long => Unit): Flow[Long] = 
      CallbackFlow { (onNext, onError, onComplete) =>
        val active = new AtomicReference(true)
        val subscription = new Subscription:
          def cancel(): Unit = active.set(false)
          def isActive: Boolean = active.get()
        
        Future {
          var count = 0L
          try
            while subscription.isActive do
              f(count)
              onNext(count)
              count += 1
              Thread.sleep(millis)
          catch
            case _: InterruptedException => // cancelled
            case ex: Throwable if subscription.isActive => onError(ex)
        }(using ExecutionContext.global)
        subscription
      }
    
    def fold[U](initial: U)(f: (U, Long) => U)(using ExecutionContext): Future[U] =
      Future.failed(new UnsupportedOperationException("Cannot fold infinite stream"))
    
    def reduce[U >: Long](f: (U, U) => U)(using ExecutionContext): Future[U] =
      Future.failed(new UnsupportedOperationException("Cannot reduce infinite stream"))
    
    def runToList()(using ExecutionContext): Future[List[Long]] =
      Future.failed(new UnsupportedOperationException("Cannot convert infinite stream to list"))
    
    def subscribe(onNext: Long => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val active = new AtomicReference(true)
      val subscription = new Subscription:
        def cancel(): Unit = active.set(false)
        def isActive: Boolean = active.get()
      
      Future:
        var count = 0L
        try
          while subscription.isActive do
            onNext(count)
            count += 1
            Thread.sleep(millis)
        catch
          case _: InterruptedException => // cancelled
          case ex: Throwable if subscription.isActive => onError(ex)
      subscription
    
    def toIterator(using ExecutionContext): Iterator[Long] =
      Iterator.iterate(0L)(_ + 1)

  private case class UnfoldFlow[V, S](seed: S, f: S => Option[(V, S)]) extends Flow[V]:
    def map[U](g: V => U): Flow[U] = 
      UnfoldFlow(seed, (s: S) => f(s).map((v, nextS) => (g(v), nextS)))
    def filter(predicate: V => Boolean): Flow[V] = 
      UnfoldFlow(seed, (s: S) => 
        @tailrec
        def loop(state: S): Option[(V, S)] =
          f(state) match
            case Some((v, nextS)) if predicate(v) => Some((v, nextS))
            case Some((_, nextS)) => loop(nextS)
            case None => None
        loop(s)
      )
    def flatMap[U](g: V => Flow[U]): Flow[U] = FlatMapFlow(this, g)
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = 
      UnfoldFlow(seed, (s: S) => 
        @tailrec
        def loop(state: S): Option[(U, S)] =
          f(state) match
            case Some((v, nextS)) if pf.isDefinedAt(v) => Some((pf(v), nextS))
            case Some((_, nextS)) => loop(nextS)
            case None => None
        loop(s)
      )
    def take(n: Int): Flow[V] = 
      if n <= 0 then Flow.empty
      else 
        UnfoldFlow((seed, 0), (state: (S, Int)) => 
          val (s, count) = state
          if count >= n then None
          else f(s).map((v, nextS) => (v, (nextS, count + 1)))
        )
    def drop(n: Int): Flow[V] = 
      if n <= 0 then this
      else 
        @tailrec
        def skipN(state: S, remaining: Int): S =
          if remaining <= 0 then state
          else f(state) match
            case Some((_, nextS)) => skipN(nextS, remaining - 1)
            case None => state
        UnfoldFlow(skipN(seed, n), f)
    def takeWhile(predicate: V => Boolean): Flow[V] = 
      UnfoldFlow(seed, (s: S) => 
        f(s) match
          case Some((v, nextS)) if predicate(v) => Some((v, nextS))
          case _ => None
      )
    def dropWhile(predicate: V => Boolean): Flow[V] = 
      @tailrec
      def skipWhile(state: S): S =
        f(state) match
          case Some((v, nextS)) if predicate(v) => skipWhile(nextS)
          case _ => state
      UnfoldFlow(skipWhile(seed), f)
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(List(this, other))
    def foreach(g: V => Unit): Flow[V] = 
      UnfoldFlow(seed, (s: S) => f(s).map((v, nextS) => { g(v); (v, nextS) }))
    
    def fold[U](initial: U)(g: (U, V) => U)(using ExecutionContext): Future[U] =
      Future:
        @tailrec
        def loop(state: S, acc: U): U =
          f(state) match
            case Some((v, nextS)) => loop(nextS, g(acc, v))
            case None => acc
        loop(seed, initial)
    
    def reduce[U >: V](g: (U, U) => U)(using ExecutionContext): Future[U] =
      Future:
        f(seed) match
          case Some((firstV, nextS)) =>
            @tailrec
            def loop(state: S, acc: U): U =
              f(state) match
                case Some((v, nextState)) => loop(nextState, g(acc, v))
                case None => acc
            loop(nextS, firstV)
          case None => throw new NoSuchElementException("Empty flow")
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      Future:
        @tailrec
        def loop(state: S, acc: List[V]): List[V] =
          f(state) match
            case Some((v, nextS)) => loop(nextS, v :: acc)
            case None => acc.reverse
        loop(seed, Nil)
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val active = new AtomicReference(true)
      val subscription = new Subscription:
        def cancel(): Unit = active.set(false)
        def isActive: Boolean = active.get()
      
      Future:
        try
          @tailrec
          def loop(state: S): Unit =
            if subscription.isActive then
              f(state) match
                case Some((v, nextS)) => 
                  onNext(v)
                  loop(nextS)
                case None => 
                  onComplete()
          loop(seed)
        catch
          case ex: Throwable if subscription.isActive => onError(ex)
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] =
      new Iterator[V]:
        var state: Option[S] = Some(seed)
        var nextValue: Option[V] = None
        
        def hasNext: Boolean =
          if nextValue.isDefined then true
          else
            state match
              case Some(s) =>
                f(s) match
                  case Some((v, nextS)) =>
                    nextValue = Some(v)
                    state = Some(nextS)
                    true
                  case None =>
                    state = None
                    false
              case None => false
        
        def next(): V =
          if hasNext then
            val v = nextValue.get
            nextValue = None
            v
          else throw new NoSuchElementException()

  private case class FlatMapFlow[V, U](source: Flow[V], f: V => Flow[U]) extends Flow[U]:
    def map[W](g: U => W): Flow[W] = FlatMapFlow(source, v => f(v).map(g))
    def filter(predicate: U => Boolean): Flow[U] = FlatMapFlow(source, v => f(v).filter(predicate))
    def flatMap[W](g: U => Flow[W]): Flow[W] = FlatMapFlow(source, v => f(v).flatMap(g))
    def collect[W](pf: PartialFunction[U, W]): Flow[W] = FlatMapFlow(source, v => f(v).collect(pf))
    def take(n: Int): Flow[U] = 
      if n <= 0 then Flow.empty else this // Simplified
    def drop(n: Int): Flow[U] = 
      if n <= 0 then this else this // Simplified
    def takeWhile(predicate: U => Boolean): Flow[U] = FlatMapFlow(source, v => f(v).takeWhile(predicate))
    def dropWhile(predicate: U => Boolean): Flow[U] = FlatMapFlow(source, v => f(v).dropWhile(predicate))
    def concat[W >: U](other: Flow[W]): Flow[W] = ConcatFlow(List(this, other))
    def merge[W >: U](other: Flow[W]): Flow[W] = MergeFlow(List(this, other))
    def foreach(g: U => Unit): Flow[U] = FlatMapFlow(source, v => f(v).foreach(g))
    
    def fold[W](initial: W)(g: (W, U) => W)(using ExecutionContext): Future[W] =
      source.fold(initial) { (acc, v) =>
        // This is blocking but necessary for this implementation
        import scala.concurrent.duration._
        import scala.concurrent.Await
        Await.result(f(v).fold(acc)(g), Duration.Inf)
      }
    
    def reduce[W >: U](g: (W, W) => W)(using ExecutionContext): Future[W] =
      val promise = Promise[W]()
      var accumulator: Option[W] = None
      subscribe(
        u => accumulator = Some(accumulator.fold(u)(g(_, u))),
        promise.failure,
        () => accumulator match
          case Some(value) => promise.success(value)
          case None => promise.failure(new NoSuchElementException("Empty flow"))
      )
      promise.future
    
    def runToList()(using ExecutionContext): Future[List[U]] =
      val promise = Promise[List[U]]()
      val buffer = mutable.ListBuffer[U]()
      subscribe(
        buffer += _,
        promise.failure,
        () => promise.success(buffer.toList)
      )
      promise.future
    
    def subscribe(onNext: U => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val subscriptions = mutable.ListBuffer[Subscription]()
      val active = new AtomicReference(true)
      var sourceCompleted = false
      var activeFlows = 0
      
      val subscription = new Subscription:
        def cancel(): Unit = 
          active.set(false)
          subscriptions.foreach(_.cancel())
        def isActive: Boolean = active.get()
      
      def checkCompletion(): Unit =
        if sourceCompleted && activeFlows == 0 then onComplete()
      
      val sourceSub = source.subscribe(
        v => 
          if subscription.isActive then
            activeFlows += 1
            val flowSub = f(v).subscribe(
              onNext,
              onError,
              () => 
                activeFlows -= 1
                checkCompletion()
            )
            subscriptions += flowSub
        ,
        onError,
        () => 
          sourceCompleted = true
          checkCompletion()
      )
      subscriptions += sourceSub
      subscription
    
    def toIterator(using ExecutionContext): Iterator[U] =
      source.toIterator.flatMap(v => f(v).toIterator)

  private case class MergeFlow[V](flows: List[Flow[V]]) extends Flow[V]:
    def map[U](f: V => U): Flow[U] = MergeFlow(flows.map(_.map(f)))
    def filter(predicate: V => Boolean): Flow[V] = MergeFlow(flows.map(_.filter(predicate)))
    def flatMap[U](f: V => Flow[U]): Flow[U] = MergeFlow(flows.map(_.flatMap(f)))
    def collect[U](pf: PartialFunction[V, U]): Flow[U] = MergeFlow(flows.map(_.collect(pf)))
    def take(n: Int): Flow[V] = 
      if n <= 0 then Flow.empty
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          val active = new AtomicReference(true)
          val subscriptions = mutable.ListBuffer[Subscription]()
          val emittedCount = new AtomicReference(0)
          
          val subscription = new Subscription:
            def cancel(): Unit = 
              active.set(false)
              subscriptions.foreach(_.cancel())
            def isActive: Boolean = active.get()
          
          var completedCount = 0
          
          flows.foreach { flow =>
            if subscription.isActive then
              val sub = flow.subscribe(
                v => 
                  if subscription.isActive then
                    val count = emittedCount.getAndUpdate(_ + 1)
                    if count < n then
                      onNext(v)
                      if count + 1 == n then
                        subscription.cancel()
                        onComplete()
                ,
                onError,
                () => 
                  completedCount += 1
                  if completedCount == flows.length && subscription.isActive then
                    onComplete()
              )(using ExecutionContext.global)
              subscriptions += sub
          }
          subscription
        }
    def drop(n: Int): Flow[V] = 
      if n <= 0 then this
      else 
        CallbackFlow { (onNext, onError, onComplete) =>
          val active = new AtomicReference(true)
          val subscriptions = mutable.ListBuffer[Subscription]()
          val droppedCount = new AtomicReference(0)
          
          val subscription = new Subscription:
            def cancel(): Unit = 
              active.set(false)
              subscriptions.foreach(_.cancel())
            def isActive: Boolean = active.get()
          
          var completedCount = 0
          
          flows.foreach { flow =>
            if subscription.isActive then
              val sub = flow.subscribe(
                v => 
                  if subscription.isActive then
                    if droppedCount.getAndUpdate(_ + 1) >= n then
                      onNext(v)
                ,
                onError,
                () => 
                  completedCount += 1
                  if completedCount == flows.length && subscription.isActive then
                    onComplete()
              )(using ExecutionContext.global)
              subscriptions += sub
          }
          subscription
        }
    def takeWhile(predicate: V => Boolean): Flow[V] = 
      MergeFlow(flows.map(_.takeWhile(predicate)))
    def dropWhile(predicate: V => Boolean): Flow[V] = 
      MergeFlow(flows.map(_.dropWhile(predicate)))
    def concat[U >: V](other: Flow[U]): Flow[U] = ConcatFlow(List(this, other))
    def merge[U >: V](other: Flow[U]): Flow[U] = MergeFlow(flows :+ other)
    def foreach(f: V => Unit): Flow[V] = MergeFlow(flows.map(_.foreach(f)))
    
    def fold[U](initial: U)(f: (U, V) => U)(using ExecutionContext): Future[U] =
      val promise = Promise[U]()
      var accumulator = initial
      subscribe(
        v => accumulator = f(accumulator, v),
        promise.failure,
        () => promise.success(accumulator)
      )
      promise.future
    
    def reduce[U >: V](f: (U, U) => U)(using ExecutionContext): Future[U] =
      val promise = Promise[U]()
      var accumulator: Option[U] = None
      subscribe(
        v => accumulator = Some(accumulator.fold(v)(f(_, v))),
        promise.failure,
        () => accumulator match
          case Some(value) => promise.success(value)
          case None => promise.failure(new NoSuchElementException("Empty flow"))
      )
      promise.future
    
    def runToList()(using ExecutionContext): Future[List[V]] =
      val promise = Promise[List[V]]()
      val buffer = mutable.ListBuffer[V]()
      subscribe(
        buffer += _,
        promise.failure,
        () => promise.success(buffer.toList)
      )
      promise.future
    
    def subscribe(onNext: V => Unit, onError: Throwable => Unit, onComplete: () => Unit)(using ExecutionContext): Subscription =
      val subscriptions = mutable.ListBuffer[Subscription]()
      val active = new AtomicReference(true)
      var completedCount = 0
      
      val subscription = new Subscription:
        def cancel(): Unit = 
          active.set(false)
          subscriptions.foreach(_.cancel())
        def isActive: Boolean = active.get()
      
      flows.foreach { flow =>
        if subscription.isActive then
          val sub = flow.subscribe(
            onNext,
            onError,
            () => 
              completedCount += 1
              if completedCount == flows.length && subscription.isActive then
                onComplete()
          )
          subscriptions += sub
      }
      subscription
    
    def toIterator(using ExecutionContext): Iterator[V] =
      // For iterator, we need to interleave - this is a simplified approach
      // that collects all values first (blocking)
      val promise = Promise[List[V]]()
      val buffer = mutable.ListBuffer[V]()
      subscribe(
        buffer += _,
        promise.failure,
        () => promise.success(buffer.toList)
      )
      import scala.concurrent.duration._
      import scala.concurrent.Await
      Await.result(promise.future, Duration.Inf).iterator
