package io.github.nicolasfara.locicope.macros

import io.github.nicolasfara.locicope.multiparty.multitier.Multitier
import io.github.nicolasfara.locicope.network.Network
import io.github.nicolasfara.locicope.placement.Peers.Peer
import io.github.nicolasfara.locicope.serialization.Encoder

import scala.annotation.tailrec
import scala.quoted.{ Expr, Quotes }

object PlacedFunctionFinder:
  inline def findPlacedFunctions(inline body: Any, network: Network): Unit =
    ${ findPlacedFunctionsImpl('body, 'network) }

  private def findPlacedFunctionsImpl(body: Expr[Any], net: Expr[Network])(using q: Quotes): Expr[Unit] =
    import q.reflect.*

    @tailrec
    def isPlacedFunction(tpe: TypeRepr): Boolean =
      tpe match
        case AppliedType(tycon, _) => isPlacedFunction(tycon)
        case t => t.typeSymbol.name == TypeRepr.of[Multitier#PlacedFunction[?, ?, ?, ?]].typeSymbol.name

    def extractOutType(tpe: TypeRepr): Option[(TypeRepr, TypeRepr, TypeRepr, TypeRepr)] =
      tpe match
        case AppliedType(_, List(peer, inType, outType, placement)) => Some((peer, inType, outType, placement))
        case _ => None

    def collect(tree: Tree): List[(Term, TypeRepr, TypeRepr, TypeRepr, TypeRepr)] = tree match
      case apply @ Apply(fun, args) =>
        val widened = apply.tpe.widen
        val collected = collect(fun) ++ args.flatMap(collect)
        if isPlacedFunction(widened) then
          extractOutType(widened) match
            case Some((peer, inType, outType, placement)) => (apply, peer, inType, outType, placement) :: Nil
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

    def getImplicitFromType(tpe: AppliedType): Term =
      Implicits.search(tpe) match
        case iss: ImplicitSearchSuccess => iss.tree
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No implicit found for type ${tpe.show}")

    val found = collect(body.asTerm).distinct
    val registrations = found.map { case (placedFunction, peer, inType, outType, placement) =>
      val encoderIn = AppliedType(TypeRepr.of[Encoder].typeSymbol.typeRef, List(inType))
      val encoderOut = AppliedType(TypeRepr.of[Encoder].typeSymbol.typeRef, List(outType))
      val encoderInImplicit = getImplicitFromType(encoderIn)
      val encoderOutImplicit = getImplicitFromType(encoderOut)

      (peer.asType, inType.asType, outType.asType, placement.asType) match
        case ('[type peer <: Peer; peer], '[type inT <: Product; inT], '[outT], '[type placement[_, _ <: Peer]; placement]) =>
          '{
            $net.registerFunction[inT, outT, placement](
              ${ placedFunction.asExprOf[Multitier#PlacedFunction[peer, inT, outT, placement]] },
            )(using
              ${ encoderInImplicit.asExprOf[Encoder[inT]] },
              ${ encoderOutImplicit.asExprOf[Encoder[outT]] },
            )
          }
        case _ =>
          report.errorAndAbort(
            s"Invalid type parameters for placed function: peer=${peer.show}, inType=${inType.show}, outType=${outType.show}, placement=${placement.show}",
          )
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
