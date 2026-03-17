/*
 * Focused excerpt from Scafi demos/src/main/scala/sims/CollectionDemo.scala
 */

package sims

import it.unibo.scafi.incarnations.BasicSimulationIncarnation.*
import Builtins.*

class CollectionIds extends AggregateProgram with SensorDefinitions with BlockC with BlockG:
  def summarize[V](sink: Boolean, acc: (V, V) => V, local: V, Null: V): V =
    C[Double, V](distanceTo3(sink), acc, local, Null)

  import PartialOrderingWithGLB.pogldouble

  def distanceTo3(src: Boolean): Double =
    G3[Double](src, 0.0, _ + nbrRange, nbrRange)(pogldouble)

  def G3[V: PartialOrderingWithGLB](source: Boolean, field: V, acc: V => V, metric: => Double): V =
    rep((Double.MaxValue, field)) { case (dist, value) =>
      mux(source) {
        (0.0, field)
      } {
        import PartialOrderingWithGLB.*
        minHoodPlusLoc[(Double, V)]((Double.PositiveInfinity, field)) {
          (nbr { dist } + metric, acc(nbr { value }))
        }(poglbTuple(pogldouble, implicitly[PartialOrderingWithGLB[V]]))
      }
    }._2

  given Bounded[Set[ID]] with
    override def top: Set[ID] = Set.empty
    override def bottom: Set[ID] = Set.empty
    override def compare(a: Set[ID], b: Set[ID]): Int = a.size.compare(b.size)

  override def main(): Set[ID] =
    summarize[Set[ID]](sense1, _ ++ _, if sense2 then Set(mid()) else Set.empty, Set.empty)
