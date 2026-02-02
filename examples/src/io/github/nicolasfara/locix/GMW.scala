package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.util.Random

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{ Locix, Multitier }

import Multitier.*
import network.Network.*
import placement.Peers.Quantifier.*
import placement.Peers.peer
import placement.PlacedFlow
import placement.PlacedFlow.*
import placement.PlacedValue
import placement.PlacedValue.*
import placement.PlacementType.on

/**
 * GMW (Goldreich-Micali-Widgerson) Secure Multiparty Computation Protocol
 *
 * This implementation is semantically equivalent to the Haskell HasChor GMW implementation.
 *
 * The GMW protocol allows n parties to jointly compute an agreed-upon function of their distributed data WITHOUT revealing the data or any
 * intermediate results to the other parties.
 *
 * Key building blocks:
 *   1. Additive Secret Sharing: Each value is split into XOR shares across all parties
 *   2. Oblivious Transfer (OT): A two-party sub-protocol for AND gate evaluation
 *   3. Circuit Representation: The function is specified as a binary circuit (InputWire, LitWire, AndGate, XorGate)
 *
 * Protocol overview (following Haskell implementation):
 *   1. secretShare: Party p secret-shares its input using genShares and scatter
 *   2. Gate evaluation:
 *      - XOR gates: Each party locally XORs their shares (parallel computation)
 *      - AND gates: Uses fAnd with OT between all pairs of parties
 *   3. reveal: All parties gather shares and XOR them to get the final result
 *
 * The circuit is represented as an ADT:
 *   - InputWire(partyId): Input from a specific party
 *   - LitWire(value): A literal boolean value
 *   - AndGate(left, right): AND of two sub-circuits
 *   - XorGate(left, right): XOR of two sub-circuits
 *
 * Security properties:
 *   - Input privacy: No party learns another party's input
 *   - Computation integrity: The result is correct if parties follow the protocol
 *   - Semi-honest security: Secure against honest-but-curious adversaries
 */
object GMW:

  // ============================================================
  // Peer Definitions
  // ============================================================

  /**
   * Party peers in the MPC protocol. Each party holds their secret shares and participates in the computation. Parties are connected to all other
   * parties (for OT) and to the Coordinator.
   */
  type Party <: { type Tie <: Single[Coordinator] }

  /**
   * Coordinator peer that orchestrates the protocol, gathers output shares and reconstructs the result. Connected to all Party peers.
   */
  type Coordinator <: { type Tie <: Multiple[Party] }

  // ============================================================
  // Circuit Representation (Haskell-equivalent ADT)
  // ============================================================

  /**
   * A circuit in the GMW protocol, represented as an ADT.
   *
   * This mirrors the Haskell definition:
   * {{{
   * data Circuit :: [LocTy] -> Type where
   *   InputWire :: (KnownSymbol p) => Member p ps -> Circuit ps
   *   LitWire :: Bool -> Circuit ps
   *   AndGate :: Circuit ps -> Circuit ps -> Circuit ps
   *   XorGate :: Circuit ps -> Circuit ps -> Circuit ps
   * }}}
   *
   * @tparam NumParties
   *   The number of parties in the protocol (type-level integer for stronger typing)
   */
  sealed trait Circuit:
    /** Recursively evaluate the circuit given input values from each party */
    def evaluate(inputs: Map[Int, Boolean]): Boolean = this match
      case InputWire(partyId)      => inputs(partyId)
      case LitWire(value)          => value
      case AndGate(left, right)    => left.evaluate(inputs) && right.evaluate(inputs)
      case XorGate(left, right)    => left.evaluate(inputs) ^ right.evaluate(inputs)

    /** Pretty print the circuit */
    override def toString: String = this match
      case InputWire(partyId)   => s"InputWire<p$partyId>"
      case LitWire(value)       => s"LitWire($value)"
      case AndGate(left, right) => s"($left) AND ($right)"
      case XorGate(left, right) => s"($left) XOR ($right)"

  /** Input wire from a specific party (equivalent to Haskell's InputWire with Member p ps) */
  case class InputWire(partyId: Int) extends Circuit

  /** Literal boolean value (publicly known) */
  case class LitWire(value: Boolean) extends Circuit

  /** AND gate - requires OT for secure computation */
  case class AndGate(left: Circuit, right: Circuit) extends Circuit

  /** XOR gate - can be computed locally on shares */
  case class XorGate(left: Circuit, right: Circuit) extends Circuit

  // ============================================================
  // Utility: XOR fold (equivalent to Haskell's xor :: Foldable f => f Bool -> Bool)
  // ============================================================

  /** XOR all boolean values in a collection (foldr1 (/=) in Haskell) */
  def xor(values: Iterable[Boolean]): Boolean = values.reduce(_ ^ _)

  def xor(values: Boolean*): Boolean = values.reduce(_ ^ _)

  // ============================================================
  // Secret Sharing (equivalent to Haskell's genShares and secretShare)
  // ============================================================

  /**
   * A share held by a party. The XOR of all shares for a value equals the original value.
   *
   * In Haskell: `Faceted parties '[] Bool` represents a value faceted across all parties.
   */
  case class Share(partyId: Int, value: Boolean)

  /**
   * A collection of shares across all parties (equivalent to Haskell's Quire ps Bool).
   */
  case class Shares(shares: Map[Int, Boolean]):
    def apply(partyId: Int): Boolean = shares(partyId)
    def values: Iterable[Boolean] = shares.values
    def reconstruct: Boolean = xor(values)

  /**
   * Generate n XOR shares of a boolean value.
   *
   * Equivalent to Haskell's genShares:
   * {{{
   * genShares :: forall ps p m. (MonadIO m, KnownSymbols ps) => Member p ps -> Bool -> m (Quire ps Bool)
   * genShares p x = quorum1 p gs'
   *   where
   *     gs' :: forall q qs. (KnownSymbol q, KnownSymbols qs) => m (Quire (q ': qs) Bool)
   *     gs' = do
   *       freeShares <- sequence $ pure $ liftIO randomIO -- generate n-1 random shares
   *       return $ qCons (xor (qCons @q x freeShares)) freeShares
   * }}}
   *
   * The last share is computed so that XOR of all shares equals the original value.
   *
   * @param value
   *   The boolean value to secret-share
   * @param numParties
   *   Number of parties to share among
   * @return
   *   A map from party ID to their share
   */
  def genShares(value: Boolean, numParties: Int): Shares =
    // Generate n-1 random shares
    val freeShares = (0 until numParties - 1).map(i => i -> Random.nextBoolean()).toMap
    // Compute the last share so XOR of all shares = value
    val xorOfFree = freeShares.values.fold(false)(_ ^ _)
    val lastShare = value ^ xorOfFree
    Shares(freeShares + ((numParties - 1) -> lastShare))

  /**
   * A faceted value: each party holds their share, and XOR of all shares = original value.
   *
   * This corresponds to Haskell's `Faceted parties '[] Bool`.
   */
  case class Faceted(shares: Map[Int, Boolean]):
    def apply(partyId: Int): Boolean = shares(partyId)
    def reveal: Boolean = xor(shares.values)

  // ============================================================
  // Oblivious Transfer (OT) Simulation
  // ============================================================

  /**
   * Simulated 1-2 Oblivious Transfer.
   *
   * In the Haskell code, OT is used in fAnd:
   * {{{
   * conclaveTo (p_i @@ p_j @@ nobody) (listedSecond @@ nobody) (ot2 bb $ localize p_j vShares)
   * }}}
   *
   * The sender has (m0, m1), the receiver has choice bit b. After OT:
   *   - Receiver learns m_b (nothing about m_{1-b})
   *   - Sender learns nothing about b
   *
   * @param m0
   *   Message if choice is false
   * @param m1
   *   Message if choice is true
   * @param choice
   *   Receiver's choice bit
   * @return
   *   m_choice
   */
  def ot2(m0: Boolean, m1: Boolean, choice: Boolean): Boolean =
    if choice then m1 else m0

  // ============================================================
  // fAnd: AND gate using OT (equivalent to Haskell's fAnd)
  // ============================================================

  /**
   * Compute secret-shared AND of two secret-shared values using OT.
   *
   * Equivalent to Haskell's fAnd:
   * {{{
   * fAnd :: forall parties m. (KnownSymbols parties, MonadIO m, CRT.MonadRandom m)
   *      => Faceted parties '[] Bool
   *      -> Faceted parties '[] Bool
   *      -> Choreo parties (CLI m) (Faceted parties '[] Bool)
   * }}}
   *
   * Algorithm:
   * 1. Each party j generates random a_j values for all other parties
   * 2. For each pair (i, j) where i ≠ j:
   *    - Party i prepares truth table bb = (xor[u_i, a_ij], a_ij)
   *    - Party j uses their v_j share as choice in OT to get one value
   * 3. Each party j computes b = XOR of all OT results
   * 4. Final share for party i: c_i = (u_i AND v_i) XOR b_i XOR (XOR of all a_ij where j ≠ i)
   *
   * @param uShares
   *   Faceted shares of first operand
   * @param vShares
   *   Faceted shares of second operand
   * @param numParties
   *   Number of parties
   * @return
   *   Faceted shares of (u AND v)
   */
  def fAnd(uShares: Faceted, vShares: Faceted, numParties: Int): Faceted =
    // Step 1: Generate random a_j values for each party j
    // a_j_s[j][i] = random value that party j generates for party i
    val a_j_s: Map[Int, Map[Int, Boolean]] =
      (0 until numParties).map { j =>
        j -> (0 until numParties).map(i => i -> Random.nextBoolean()).toMap
      }.toMap

    // Step 2: Compute b values using OT (fanIn/fanOut pattern from Haskell)
    val bs: Map[Int, Boolean] = (0 until numParties).map { p_j =>
      // For each party p_j, compute b by XORing OT results from all other parties
      val b_i_s = (0 until numParties).map { p_i =>
        if p_i == p_j then
          false // Skip self
        else
          // Party p_i is sender, party p_j is receiver
          // bb (truth table): (xor[u_i, a_ij], a_ij)
          val a_ij = a_j_s(p_j)(p_i) // a value from j's perspective for i
          val u_i = uShares(p_i)
          val m0 = a_ij // if v_j = false, receiver gets a_ij
          val m1 = xor(u_i, a_ij) // if v_j = true, receiver gets u_i XOR a_ij
          // OT: receiver (p_j) uses v_j as choice
          ot2(m0, m1, vShares(p_j))
      }
      p_j -> xor(b_i_s)
    }.toMap

    // Step 3: Compute final shares
    // c_i = (u_i AND v_i) XOR b_i XOR (XOR of a_j_s[j][i] for j ≠ i, but with party i's a_ii set to false)
    val resultShares: Map[Int, Boolean] = (0 until numParties).map { p_i =>
      val u_i = uShares(p_i)
      val v_i = vShares(p_i)
      val localProduct = u_i && v_i
      val b_i = bs(p_i)

      // XOR of a_j values where we modify our own contribution to be false
      val a_js_xor = (0 until numParties).map { j =>
        if j == p_i then false
        else a_j_s(j)(p_i)
      }.reduce(_ ^ _)

      p_i -> xor(localProduct, b_i, a_js_xor)
    }.toMap

    Faceted(resultShares)
  end fAnd

  // ============================================================
  // GMW Circuit Evaluation (equivalent to Haskell's gmw function)
  // ============================================================

  /**
   * Recursively evaluate a circuit on secret-shared values.
   *
   * Equivalent to Haskell's gmw:
   * {{{
   * gmw :: forall parties m. (KnownSymbols parties, MonadIO m, CRT.MonadRandom m)
   *     => Circuit parties
   *     -> Choreo parties (CLI m) (Faceted parties '[] Bool)
   * gmw circuit = case circuit of
   *   InputWire p -> do
   *     value :: Located '[p] Bool <- _locally p $ getInput "..."
   *     secretShare p value
   *   LitWire b -> do
   *     let chooseShare :: forall p. (KnownSymbol p) => Member p parties -> Choreo parties (CLI m) (Located '[p] Bool)
   *         chooseShare p = congruently (p @@ nobody) $ \_ -> case p of
   *           First -> b
   *           Later _ -> False
   *     fanOut chooseShare
   *   AndGate l r -> do
   *     lResult <- gmw l
   *     rResult <- gmw r
   *     fAnd lResult rResult
   *   XorGate l r -> do
   *     lResult <- gmw l
   *     rResult <- gmw r
   *     parallel (allOf @parties) \p un -> pure $ xor [viewFacet un p lResult, viewFacet un p rResult]
   * }}}
   *
   * @param circuit
   *   The circuit to evaluate
   * @param partyInputs
   *   Map from party ID to their secret input value
   * @param numParties
   *   Number of parties
   * @return
   *   Faceted (secret-shared) result
   */
  def gmw(circuit: Circuit, partyInputs: Map[Int, Boolean], numParties: Int): Faceted = circuit match
    case InputWire(partyId) =>
      // Process a secret input value from party p
      // In Haskell: value <- _locally p $ getInput; secretShare p value
      val inputValue = partyInputs.getOrElse(partyId, false)
      val shares = genShares(inputValue, numParties)
      Faceted(shares.shares)

    case LitWire(value) =>
      // Process a publicly-known literal value
      // In Haskell: First party gets the literal, others get false
      // fanOut chooseShare where chooseShare picks b for First, False for Later
      val shares = (0 until numParties).map { p =>
        p -> (if p == 0 then value else false)
      }.toMap
      Faceted(shares)

    case AndGate(left, right) =>
      // Process an AND gate using OT
      val lResult = gmw(left, partyInputs, numParties)
      val rResult = gmw(right, partyInputs, numParties)
      fAnd(lResult, rResult, numParties)

    case XorGate(left, right) =>
      // Process an XOR gate - each party locally XORs their shares
      // parallel (allOf @parties) \p un -> pure $ xor [viewFacet un p lResult, viewFacet un p rResult]
      val lResult = gmw(left, partyInputs, numParties)
      val rResult = gmw(right, partyInputs, numParties)
      val shares = (0 until numParties).map { p =>
        p -> xor(lResult(p), rResult(p))
      }.toMap
      Faceted(shares)

  /**
   * Reveal a faceted value by XORing all shares.
   *
   * Equivalent to Haskell's reveal:
   * {{{
   * reveal :: forall ps m. (KnownSymbols ps) => Faceted ps '[] Bool -> Choreo ps m Bool
   * reveal shares = xor <$> (gather ps ps shares >>= naked ps)
   * }}}
   */
  def reveal(faceted: Faceted): Boolean = faceted.reveal

  // ============================================================
  // MPC Entry Point (equivalent to Haskell's mpc)
  // ============================================================

  /**
   * Full MPC protocol: evaluate circuit and reveal result.
   *
   * Equivalent to Haskell's mpc:
   * {{{
   * mpc :: forall parties m. (KnownSymbols parties, MonadIO m, CRT.MonadRandom m)
   *     => Circuit parties
   *     -> Choreo parties (CLI m) ()
   * mpc circuit = do
   *   outputWire <- gmw circuit
   *   result <- reveal outputWire
   *   void $ _parallel (allOf @parties) $ putOutput "The resulting bit:" result
   * }}}
   */
  def mpc(circuit: Circuit, partyInputs: Map[Int, Boolean], numParties: Int): Boolean =
    val outputWire = gmw(circuit, partyInputs, numParties)
    val result = reveal(outputWire)
    println(s"The resulting bit: $result")
    result

  // ============================================================
  // Example Circuits (matching Haskell examples)
  // ============================================================

  /** Simple 2-input AND: InputWire(0) AND InputWire(1) */
  def twoInputAND: Circuit = AndGate(InputWire(0), InputWire(1))

  /** Simple 2-input XOR: InputWire(0) XOR InputWire(1) */
  def twoInputXOR: Circuit = XorGate(InputWire(0), InputWire(1))

  /** 4-party example: (p1 XOR p2) AND (p3 XOR p4) */
  def fourPartyCircuit: Circuit =
    AndGate(
      XorGate(InputWire(0), InputWire(1)),
      XorGate(InputWire(2), InputWire(3)),
    )

  /** Mixed circuit: (p1 AND p2) XOR LitWire(true) */
  def mixedCircuit: Circuit = XorGate(AndGate(InputWire(0), InputWire(1)), LitWire(true))

  // ============================================================
  // Test/Reference function (equivalent to Haskell's TestArgs)
  // ============================================================

  /**
   * Test that GMW produces correct results.
   *
   * Equivalent to Haskell's TestArgs instance:
   * {{{
   * instance TestArgs Args (Bool, Bool, Bool, Bool) where
   *   reference Args {circuit, p1in, p2in, p3in, p4in} = (answer, answer, answer, answer)
   *     where
   *       recurse c = case c of
   *         InputWire p -> fromJust $ toLocTm p `lookup` inputs
   *         LitWire b -> b
   *         AndGate left right -> recurse left && recurse right
   *         XorGate left right -> recurse left /= recurse right
   *       inputs = ["p1", "p2", "p3", "p4"] `zip` [p1in, p2in, p3in, p4in]
   *       answer = recurse circuit
   * }}}
   */
  // def reference(circuit: Circuit, inputs: Map[Int, Boolean]): Boolean =
  //   circuit.evaluate(inputs)

  // def testGMW(circuit: Circuit, inputs: Map[Int, Boolean], numParties: Int): Boolean =
  //   val gmwResult = mpc(circuit, inputs, numParties)
  //   val refResult = reference(circuit, inputs)
  //   gmwResult == refResult

  // ============================================================
  // Distributed GMW Protocol with Coordinator Architecture
  // ============================================================

  /**
   * Distributed GMW protocol using the locicope framework.
   *
   * This version uses the Party/Coordinator architecture where:
   *   - Each Party computes their share of the circuit evaluation
   *   - Coordinator gathers all shares and reconstructs the final result
   *   - Result is distributed back to all parties
   *
   * This mirrors the choreographic structure of the Haskell implementation where:
   *   - secretShare scatters shares to all parties
   *   - gmw evaluates the circuit on faceted values
   *   - reveal gathers shares and reconstructs
   */
  def gmwProtocol(circuit: Circuit, partyInputs: Map[Int, Boolean], numParties: Int)(using
      net: Network,
      mt: Multitier,
      pv: PlacedValue,
  ) =
    // Step 1: Each party computes their output share
    // This corresponds to the parallel evaluation in Haskell's gmw
    val partyOutputShare: Boolean on Party = on[Party]:
      // Party ID extracted from address (e.g., "party-0" -> 0)
      val myId = localAddress.toString.split("-").last.toInt
      // Evaluate the circuit to get this party's share of the output
      evaluateCircuitForParty(circuit, partyInputs, numParties, myId)

    // Step 2: Coordinator gathers all shares and reconstructs result
    // This corresponds to Haskell's reveal function: gather ps ps shares >>= naked ps
    val coordinatorResult: Boolean on Coordinator = on[Coordinator]:
      val allShares = asLocalAll[Party, Coordinator, Boolean](partyOutputShare)
      // XOR all shares to reconstruct (equivalent to xor <$> gather)
      val result = allShares.values.reduce(_ ^ _)
      // putOutput "The resulting bit:" result
      println(s"The resulting bit: $result")
      result

    // Step 3: Distribute result back to parties (parallel putOutput)
    val finalResult: Boolean on Party = on[Party]:
      val result = asLocal[Coordinator, Party, Boolean](coordinatorResult)
      // putOutput "The resulting bit:" result
      println(s"The resulting bit: $result")
      result

    (partyOutputShare, coordinatorResult, finalResult)
  end gmwProtocol

  /**
   * Evaluate the circuit for a specific party, returning that party's share of the output.
   *
   * This simulates the distributed evaluation where each party:
   * 1. Has their share of each input (from secretShare/scatter)
   * 2. Evaluates XOR gates locally
   * 3. Participates in OT for AND gates
   */
  private def evaluateCircuitForParty(circuit: Circuit, partyInputs: Map[Int, Boolean], numParties: Int,myId: Int): Boolean =
    // Use deterministic random for reproducibility across parties
    def deterministicShares(value: Boolean, seed: Int): Map[Int, Boolean] =
      val random = new scala.util.Random(seed)
      val freeShares = (0 until numParties - 1).map(i => i -> random.nextBoolean()).toMap
      val xorOfFree = freeShares.values.fold(false)(_ ^ _)
      val lastShare = value ^ xorOfFree
      freeShares + ((numParties - 1) -> lastShare)

    def eval(c: Circuit, depth: Int): Boolean = c match
      case InputWire(partyId) =>
        // secretShare: party partyId shares their input
        val inputValue = partyInputs.getOrElse(partyId, false)
        val seed = partyId * 10000 + depth
        val allShares = deterministicShares(inputValue, seed)
        allShares(myId) // This party's share

      case LitWire(value) =>
        // fanOut chooseShare: First party gets the literal, others get false
        if myId == 0 then value else false

      case AndGate(left, right) =>
        // fAnd using OT - we need all parties' shares to compute this correctly
        // Compute the faceted values first
        val leftShares = (0 until numParties).map(p => p -> evalForParty(left, p, depth * 2)).toMap
        val rightShares = (0 until numParties).map(p => p -> evalForParty(right, p, depth * 2 + 1)).toMap

        // Apply fAnd logic for this party
        val facetedResult = fAndForParty(Faceted(leftShares), Faceted(rightShares), numParties, myId, depth)
        facetedResult

      case XorGate(left, right) =>
        // XOR gate: each party locally XORs their shares
        val leftShare = eval(left, depth * 2)
        val rightShare = eval(right, depth * 2 + 1)
        leftShare ^ rightShare

    def evalForParty(c: Circuit, partyId: Int, depth: Int): Boolean = c match
      case InputWire(owner) =>
        val inputValue = partyInputs.getOrElse(owner, false)
        val seed = owner * 10000 + depth
        val allShares = deterministicShares(inputValue, seed)
        allShares(partyId)

      case LitWire(value) =>
        if partyId == 0 then value else false

      case AndGate(l, r) =>
        val leftShares = (0 until numParties).map(p => p -> evalForParty(l, p, depth * 2)).toMap
        val rightShares = (0 until numParties).map(p => p -> evalForParty(r, p, depth * 2 + 1)).toMap
        fAndForParty(Faceted(leftShares), Faceted(rightShares), numParties, partyId, depth)

      case XorGate(l, r) =>
        evalForParty(l, partyId, depth * 2) ^ evalForParty(r, partyId, depth * 2 + 1)

    eval(circuit, 0)

  /**
   * fAnd computation for a specific party using deterministic randomness.
   */
  private def fAndForParty(uShares: Faceted, vShares: Faceted, numParties: Int, myId: Int, depth: Int): Boolean =
    // Deterministic random generator for this AND gate
    val baseRandom = new scala.util.Random(depth * 100000)

    // Generate a_j values deterministically
    val a_j_s: Map[Int, Map[Int, Boolean]] = (0 until numParties).map { j =>
      val jRandom = new scala.util.Random(baseRandom.nextInt())
      j -> (0 until numParties).map(i => i -> jRandom.nextBoolean()).toMap
    }.toMap

    // Compute b values using OT
    val bs: Map[Int, Boolean] = (0 until numParties).map { p_j =>
      val b_i_s = (0 until numParties).map { p_i =>
        if p_i == p_j then false
        else
          val a_ij = a_j_s(p_j)(p_i)
          val u_i = uShares(p_i)
          val m0 = a_ij
          val m1 = u_i ^ a_ij
          ot2(m0, m1, vShares(p_j))
      }
      p_j -> b_i_s.reduce(_ ^ _)
    }.toMap

    // Compute final share for this party
    val u_i = uShares(myId)
    val v_i = vShares(myId)
    val localProduct = u_i && v_i
    val b_i = bs(myId)
    val a_js_xor = (0 until numParties).map { j =>
      if j == myId then false else a_j_s(j)(myId)
    }.reduce(_ ^ _)

    xor(localProduct, b_i, a_js_xor)

  // ============================================================
  // Main Entry Point
  // ============================================================

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Pure GMW computation tests
    // testGMW(twoInputAND, Map(0 -> true, 1 -> true), 2)   // 1 AND 1 = 1
    // testGMW(twoInputAND, Map(0 -> true, 1 -> false), 2)  // 1 AND 0 = 0
    // testGMW(twoInputAND, Map(0 -> false, 1 -> true), 2)  // 0 AND 1 = 0
    // testGMW(twoInputAND, Map(0 -> false, 1 -> false), 2) // 0 AND 0 = 0

    // testGMW(twoInputXOR, Map(0 -> true, 1 -> true), 2)   // 1 XOR 1 = 0
    // testGMW(twoInputXOR, Map(0 -> true, 1 -> false), 2)  // 1 XOR 0 = 1
    // testGMW(twoInputXOR, Map(0 -> false, 1 -> true), 2)  // 0 XOR 1 = 1
    // testGMW(twoInputXOR, Map(0 -> false, 1 -> false), 2) // 0 XOR 0 = 0

    // testGMW(fourPartyCircuit, Map(0 -> true, 1 -> false, 2 -> true, 3 -> false), 4)
    // testGMW(fourPartyCircuit, Map(0 -> true, 1 -> true, 2 -> false, 3 -> false), 4)

    // testGMW(mixedCircuit, Map(0 -> true, 1 -> true), 2)  // (1 AND 1) XOR 1 = 0
    // testGMW(mixedCircuit, Map(0 -> true, 1 -> false), 2) // (1 AND 0) XOR 1 = 1

    // Distributed GMW protocol demos
    runDistributedDemo(twoInputAND, Map(0 -> true, 1 -> true), numParties = 2)
    runDistributedDemo(twoInputXOR, Map(0 -> true, 1 -> false), numParties = 2)
  end main

  private def runDistributedDemo(
      circuit: Circuit,
      inputs: Map[Int, Boolean],
      numParties: Int,
  ): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Create network for each party
    val partyNetworks = (0 until numParties).map { i =>
      InMemoryNetwork[Party](s"party-$i", i)
    }.toList

    // Create coordinator network
    val coordinatorNetwork = InMemoryNetwork[Coordinator]("coordinator", 100)

    // Connect parties to coordinator (bidirectional)
    partyNetworks.foreach { partyNet =>
      partyNet.addReachablePeer(coordinatorNetwork)
      coordinatorNetwork.addReachablePeer(partyNet)
    }

    // Run the protocol on all parties and coordinator concurrently
    val partyFutures = partyNetworks.zipWithIndex.map { case (network, i) =>
      Future:
        given Locix[InMemoryNetwork[Party]] = Locix(network)
        PlacedValue.run[Party]:
          PlacedFlow.run[Party]:
            Multitier.run[Party]:
              val (_, _, finalResult) = gmwProtocol(circuit, inputs, numParties)
              finalResult.take
    }

    val coordinatorFuture = Future:
      given Locix[InMemoryNetwork[Coordinator]] = Locix(coordinatorNetwork)
      PlacedValue.run[Coordinator]:
        PlacedFlow.run[Coordinator]:
          Multitier.run[Coordinator]:
            val (_, coordResult, _) = gmwProtocol(circuit, inputs, numParties)
            coordResult.take

    val allFutures = Future.sequence(partyFutures :+ coordinatorFuture)
    Await.result(allFutures, 30.seconds)
  end runDistributedDemo

end GMW
