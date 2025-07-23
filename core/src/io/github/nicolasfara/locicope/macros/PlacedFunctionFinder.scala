package io.github.nicolasfara.locicope.macros

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.serialization.Encoder

import java.util.UUID
import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes}

object PlacedFunctionFinder:
  inline def findPlacedFunctions(inline body: Any, network: Network): Unit =
    ${ findPlacedFunctionsImpl('body, 'network) }

  private def findPlacedFunctionsImpl(body: Expr[Any], net: Expr[Network])(using q: Quotes): Expr[Unit] =
    import q.reflect.*

    @tailrec
    def isPlacedFunction(tpe: TypeRepr): Boolean =
      tpe match
        case AppliedType(tycon, _) => isPlacedFunction(tycon)
        case t => t.typeSymbol.name == "PlacedFunction"

    def extractOutType(tpe: TypeRepr): Option[(TypeRepr, TypeRepr)] =
      tpe match
        case AppliedType(_, List(_, inType, outType, _)) => Some((inType, outType))
        case _ => None

    def collect(tree: Tree): List[(Term, TypeRepr, TypeRepr)] = tree match
      case apply @ Apply(fun, args) =>
        val widened = apply.tpe.widen
        val collected = collect(fun) ++ args.flatMap(collect)
        if isPlacedFunction(widened) then
          extractOutType(widened) match
            case Some((inType, outType)) => (apply, inType, outType) :: Nil
            case None => Nil
        else collected
      case TypeApply(fun, args) => collect(fun) ++ args.flatMap(collect)
      case Select(qual, _) => collect(qual)
      case Block(stats, expr) => stats.flatMap(collect) ++ collect(expr)
      case Inlined(_, bindings, expansion) => bindings.flatMap(collect) ++ collect(expansion)
      case ValDef(_, _, Some(rhs)) => collect(rhs)
      case DefDef(_, _, _, Some(rhs)) => collect(rhs)
      case ident @ Ident(_) => collect(ident.symbol.tree)
      case _ => Nil

    val found = collect(body.asTerm)
//    if found.nonEmpty then report.info(s"==> Total found ${UUID.randomUUID()}: ${found.map(_._1)}")

    val registrations = found.map { case (placedFunction, inType, outType) =>
      val encoderIn = AppliedType(TypeRepr.of[Encoder].typeSymbol.typeRef, List(inType))
      val encoderOut = AppliedType(TypeRepr.of[Encoder].typeSymbol.typeRef, List(outType))
      val encoderInImplicit = Implicits.search(encoderIn) match
        case iss: ImplicitSearchSuccess => iss.tree
        case _: ImplicitSearchFailure => report.errorAndAbort(s"No ${encoderIn.show} found in implicit scope")
      val encoderOutImplicit = Implicits.search(encoderOut) match
        case iss: ImplicitSearchSuccess => iss.tree
        case _: ImplicitSearchFailure => report.errorAndAbort(s"No ${encoderOut.show} found in implicit scope")

      (inType.asType, outType.asType) match
        case ('[inT], '[outT]) =>
          '{
            $net.registerFunction[inT, outT, [_, _ <: Peer] =>> Any](
              ${ placedFunction.asExprOf },
            )(using
              ${ encoderInImplicit.asExprOf[Encoder[inT]] },
              ${ encoderOutImplicit.asExprOf[Encoder[outT]] },
            )
          }
        case _ => report.errorAndAbort(s"Placed function with incompatible types: inType=${inType.show}, outType=${outType.show}")
    }

    val block = registrations match
      case Nil => '{ () }
      case single :: Nil => single
      case multiple =>
        val statements = multiple.init.map(_.asTerm)
        val lastExpr = multiple.last
        Block(statements, lastExpr.asTerm).asExprOf[Unit]

    block
  end findPlacedFunctionsImpl
end PlacedFunctionFinder
