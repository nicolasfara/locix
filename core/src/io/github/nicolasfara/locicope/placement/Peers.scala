package io.github.nicolasfara.locicope.placement

import scala.quoted.*

import Peers.Quantifier.{ Multiple, Single }

/**
 * Object containing definitions for peers in a network.
 *
 * Peers are the basic building blocks of a network, representing entities that can communicate with each other.
 */
object Peers:

  opaque type PeerRepr = PeerReprImpl

  extension (peerRepr: PeerRepr)
    def baseTypeRepr: String = peerRepr.baseTypeRepr
    infix def <:<(base: PeerRepr): Boolean =
      peerRepr.baseTypeRepr == base.baseTypeRepr || peerRepr.supertypes.contains(base.baseTypeRepr)

  private final case class PeerReprImpl(baseTypeRepr: String, supertypes: List[String]):
    override def toString: String = s"'$baseTypeRepr'${supertypes.mkString(" <: '", ", ", "'")}"

  inline def peer[T <: Peer]: PeerRepr = ${ peerReprImpl[T] }

  private def peerReprImpl[T: Type](using Quotes): Expr[PeerRepr] =
    import quotes.reflect.*

    def collectBasesOfType(tpe: TypeRepr): List[Symbol] =
      tpe match
        case Refinement(parent, _, _) =>
          collectBasesOfType(parent)
        case AndType(left, right) =>
          collectBasesOfType(left) ++ collectBasesOfType(right)
        case _ if tpe.typeSymbol.exists =>
          collectBasesOfSymbol(tpe.typeSymbol)
        case _ =>
          List.empty

    def collectBasesOfSymbol(symbol: Symbol): List[Symbol] =
      symbol.info match
        case TypeBounds(_, hi) =>
          symbol :: collectBasesOfType(hi)
        case _ =>
          List.empty

    val types = collectBasesOfSymbol(TypeRepr.of[T].typeSymbol).map(_.fullName)
    '{ PeerReprImpl(${ Expr(types.head) }, ${ Expr(types.tail) }) }
  end peerReprImpl

  /**
   * Prototype of a peer in the network.
   */
  type Peer = { type Tie }

  /**
   * Prototype of a peer in the network which is tied to a single other [[P]] peer.
   *
   * {{{
   * type Client <: { type Tie <: Single[Server] }
   * type Server <: { type Tie <: Single[Client] }
   * }}}
   */
  type TiedToSingle[P <: Peer] = { type Tie <: Single[P] }

  /**
   * Prototype of a peer in the network which is tied to multiple other [[P]] peers.
   *
   * {{{
   * type Client <: { type Tie <: Multiple[Client] }
   * }}}
   */
  type TiedToMultiple[P <: Peer] = { type Tie <: Multiple[P] }

  /**
   * Prototype of a peer in the network which is tied to other [[P]] peers. The quantifier can be either [[Single]] or [[Multiple]].
   */
  type TiedWith[P <: Peer] = { type Tie <: Quantifier[P] }

  /**
   * Tie cardinality of a peer.
   *
   * {{{
   * type Client <: { type Tie <: Single[Server] }
   * type Server <: { type Tie <: Multiple[Client] }
   * }}}
   *
   * @tparam P
   *   the other peer type on which the quantifier is defined.
   */
  enum Quantifier[+P <: Peer]:
    case Multiple()
    case Single()
end Peers
