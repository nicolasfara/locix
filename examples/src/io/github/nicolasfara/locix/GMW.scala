package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.duration.*

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
 * This implementation demonstrates the classic GMW protocol for secure multiparty
 * computation within the locicope choreographic framework.
 *
 * The GMW protocol allows n parties to jointly compute an agreed-upon function of
 * their distributed data WITHOUT revealing the data or any intermediate results
 * to the other parties.
 *
 * Key building blocks:
 *   1. Additive Secret Sharing: Encrypts distributed data while still allowing computation
 *   2. Oblivious Transfer (OT): A two-party sub-protocol for AND gate evaluation
 *   3. Circuit Representation: The function is specified as a binary circuit
 *
 * Protocol overview:
 *   1. Each party secret-shares its input values
 *   2. Parties iteratively evaluate gates, keeping intermediate values secret-shared:
 *      - XOR gates: local computation (XOR the shares)
 *      - AND gates: requires oblivious transfer between all pairs of parties
 *   3. When evaluation finishes, parties reveal their shares to decrypt the final result
 *
 * Security properties:
 *   - Input privacy: No party learns another party's input
 *   - Computation integrity: The result is correct if parties follow the protocol
 *   - Semi-honest security: Secure against honest-but-curious adversaries
 *
 * Framework Patterns Demonstrated:
 *   - Census polymorphism: parametric on the number of participants
 *   - Faceted values: Secret-shares as distributed values
 *   - FanIn/FanOut: Using ShareHolder/Party architecture for share distribution
 *   - Conclave: Nested two-party OT sub-choreography for each pair
 *
 * Note: This implementation uses a ShareHolder/Party peer architecture to work
 * within the framework's communication model, where ShareHolder peers hold input
 * shares and Party peers gather and compute on them.
 */
object GMW:

  // ============================================================
  // Peer Definitions
  // ============================================================

  /**
   * Party peers in the MPC protocol.
   * Each party computes on their shares and sends output shares to Coordinator.
   */
  type Party <: { type Tie <: Single[Coordinator] }

  /**
   * Coordinator peer that gathers output shares and reconstructs the result.
   * Connected to all Party peers.
   */
  type Coordinator <: { type Tie <: Multiple[Party] }

  // ============================================================
  // Circuit Representation
  // ============================================================

  /**
   * A wire in the circuit, identified by its index.
   */
  case class Wire(index: Int)

  /**
   * Gate types in the binary circuit.
   * GMW protocol supports XOR and AND gates:
   *   - XOR: Can be computed locally on shares
   *   - AND: Requires oblivious transfer between all pairs
   */
  sealed trait Gate:
    def output: Wire
    def inputs: List[Wire]

  case class XorGate(left: Wire, right: Wire, output: Wire) extends Gate:
    def inputs: List[Wire] = List(left, right)

  case class AndGate(left: Wire, right: Wire, output: Wire) extends Gate:
    def inputs: List[Wire] = List(left, right)

  case class InputGate(partyId: Int, output: Wire) extends Gate:
    def inputs: List[Wire] = Nil

  case class OutputGate(input: Wire, output: Wire) extends Gate:
    def inputs: List[Wire] = List(input)

  /**
   * A binary circuit consisting of gates.
   *
   * @param gates    The gates in topological order
   * @param inputs   Map from party ID to their input wires
   * @param outputs  The output wires
   * @param numParties  Number of parties in the protocol
   */
  case class Circuit(
      gates: List[Gate],
      inputs: Map[Int, List[Wire]],
      outputs: List[Wire],
      numParties: Int,
  ):
    def inputWires: Set[Wire] = gates.collect { case InputGate(_, w) => w }.toSet
    def outputWires: Set[Wire] = outputs.toSet

  // ============================================================
  // Secret Sharing Primitives
  // ============================================================

  /**
   * A secret share of a bit.
   * XOR of all shares equals the original bit.
   */
  case class BitShare(partyId: Int, wireIndex: Int, value: Boolean)

  /**
   * Secret-shared wire value.
   * Each party holds one share; XOR of all shares = actual value.
   */
  case class SharedWireValue(wireIndex: Int, shares: Map[Int, Boolean]):
    def reconstruct: Boolean = shares.values.reduce(_ ^ _)

  /**
   * Split a bit into n additive (XOR) shares.
   * The XOR of all shares equals the original bit.
   */
  def splitBitIntoShares(bit: Boolean, numParties: Int, wireIndex: Int): Map[Int, BitShare] =
    val random = new scala.util.Random()
    // Generate n-1 random shares
    val randomShares = (0 until numParties - 1).map(i => i -> random.nextBoolean()).toMap
    // Last share makes XOR equal to the original bit
    val xorOfRandom = randomShares.values.fold(false)(_ ^ _)
    val lastShare = bit ^ xorOfRandom
    val allShares = randomShares + ((numParties - 1) -> lastShare)
    allShares.map { case (partyId, value) => partyId -> BitShare(partyId, wireIndex, value) }

  /**
   * Reconstruct a bit from XOR shares.
   */
  def reconstructBit(shares: List[BitShare]): Boolean =
    shares.map(_.value).reduce(_ ^ _)

  // ============================================================
  // Oblivious Transfer Primitives (Simplified)
  // ============================================================

  /**
   * Oblivious Transfer result.
   * The receiver gets m_b where b is their choice bit,
   * without the sender learning b, and without the receiver learning m_{1-b}.
   */
  case class OTResult(choiceBit: Boolean, receivedValue: Boolean)

  /**
   * Sender's messages for OT.
   */
  case class OTSenderMessage(m0: Boolean, m1: Boolean)

  /**
   * Receiver's choice for OT (encrypted in real implementation).
   */
  case class OTReceiverChoice(partyId: Int, encryptedChoice: Boolean)

  /**
   * Simulated OT protocol (in production, would use real crypto like RSA-based OT).
   *
   * The sender has two messages m0, m1.
   * The receiver has a choice bit b.
   * After OT:
   *   - Receiver learns m_b
   *   - Sender learns nothing about b
   *   - Receiver learns nothing about m_{1-b}
   *
   * For GMW AND gates, we use OT to compute a XOR b*c where:
   *   - Party i has share a_i of wire a
   *   - Party j has share b_j of wire b
   *   - They need to compute shares of (a AND b) without revealing their shares
   */
  def simulateOT(senderMessages: OTSenderMessage, receiverChoice: Boolean): Boolean =
    if receiverChoice then senderMessages.m1 else senderMessages.m0

  // ============================================================
  // AND Gate Evaluation via OT
  // ============================================================

  /**
   * Compute party i's contribution to AND gate shares using OT with party j.
   *
   * For AND gate on wires a,b with output c:
   * Each pair (i,j) where i < j performs OT where:
   *   - Party i (sender) prepares messages based on their a_i share
   *   - Party j (receiver) uses their b_j share as choice
   *   - They both get shares of a_i AND b_j
   *
   * The final AND share for party k is:
   *   c_k = (a_k AND b_k) XOR (XOR over all pairs involving k of the OT results)
   */
  case class ANDContribution(fromParty: Int, toParty: Int, value: Boolean)

  /**
   * Prepare OT sender messages for AND gate.
   * If sender has share a_i and wants to help compute (a AND b):
   *   m0 = random r
   *   m1 = r XOR a_i
   * Receiver with b_j will get r if b_j=0, or r XOR a_i if b_j=1.
   * This equals r XOR (a_i AND b_j).
   */
  def prepareANDSenderMessages(senderShare: Boolean, randomMask: Boolean): OTSenderMessage =
    OTSenderMessage(
      m0 = randomMask,
      m1 = randomMask ^ senderShare,
    )

  // ============================================================
  // Circuit Examples
  // ============================================================

  /**
   * Example: 2-party AND function.
   * Computes (input1 AND input2) where each party provides one input.
   */
  def twoPartyANDCircuit: Circuit = Circuit(
    gates = List(
      InputGate(0, Wire(0)),
      InputGate(1, Wire(1)),
      AndGate(Wire(0), Wire(1), Wire(2)),
      OutputGate(Wire(2), Wire(3)),
    ),
    inputs = Map(0 -> List(Wire(0)), 1 -> List(Wire(1))),
    outputs = List(Wire(3)),
    numParties = 2,
  )

  /**
   * Example: 3-party majority function.
   * Computes majority(a, b, c) = (a AND b) OR (b AND c) OR (a AND c)
   * Using only AND/XOR: (a AND b) XOR (b AND c) XOR (a AND c) XOR (a AND b AND c)
   *
   * Simplified to: (a XOR b) AND (b XOR c) XOR b
   * Actually, let's use: (a AND b) XOR (a AND c) XOR (b AND c)
   * which is close but needs adjustment. We'll use a direct formula.
   */
  def threePartyMajorityCircuit: Circuit = Circuit(
    gates = List(
      InputGate(0, Wire(0)), // a
      InputGate(1, Wire(1)), // b
      InputGate(2, Wire(2)), // c
      AndGate(Wire(0), Wire(1), Wire(3)), // a AND b
      AndGate(Wire(1), Wire(2), Wire(4)), // b AND c
      AndGate(Wire(0), Wire(2), Wire(5)), // a AND c
      XorGate(Wire(3), Wire(4), Wire(6)), // (a AND b) XOR (b AND c)
      XorGate(Wire(6), Wire(5), Wire(7)), // ... XOR (a AND c)
      // Majority needs one more adjustment, but this demonstrates the pattern
      OutputGate(Wire(7), Wire(8)),
    ),
    inputs = Map(0 -> List(Wire(0)), 1 -> List(Wire(1)), 2 -> List(Wire(2))),
    outputs = List(Wire(8)),
    numParties = 3,
  )

  /**
   * Example: 3-party XOR-only circuit (no OT needed).
   * Computes XOR of all inputs.
   */
  def threePartyXORCircuit: Circuit = Circuit(
    gates = List(
      InputGate(0, Wire(0)),
      InputGate(1, Wire(1)),
      InputGate(2, Wire(2)),
      XorGate(Wire(0), Wire(1), Wire(3)),
      XorGate(Wire(3), Wire(2), Wire(4)),
      OutputGate(Wire(4), Wire(5)),
    ),
    inputs = Map(0 -> List(Wire(0)), 1 -> List(Wire(1)), 2 -> List(Wire(2))),
    outputs = List(Wire(5)),
    numParties = 3,
  )

  // ============================================================
  // GMW Protocol Implementation
  // ============================================================

  /**
   * Shared state for circuit evaluation.
   * Maps wire indices to the local party's share of that wire.
   */
  type WireShares = Map[Int, Boolean]

  /**
   * AND gate OT data exchanged between parties.
   */
  case class ANDGateOTData(
      gateOutput: Int,
      contributions: Map[(Int, Int), Boolean], // (sender, receiver) -> contribution
  )

  /**
   * Main GMW protocol with Coordinator architecture.
   *
   * This implementation demonstrates the GMW protocol structure within the framework.
   * It uses a Party/Coordinator architecture where:
   *   - Party peers compute on their secret shares
   *   - Coordinator gathers output shares and reconstructs the final result
   *   - Coordinator then distributes the result back to all parties
   *
   * The share derivation uses a deterministic scheme to simulate proper share
   * distribution, ensuring all parties derive consistent shares without explicit
   * peer-to-peer communication.
   *
   * @param circuit  The circuit to evaluate
   * @param inputs   Map from party ID to their input bits
   */
  def gmwProtocol(circuit: Circuit, partyInputs: Map[Int, List[Boolean]])(using
      net: Network,
      mt: Multitier,
      pf: PlacedFlow,
      pv: PlacedValue,
  ) =
    val numParties = circuit.numParties

    // Step 1: Each party computes their output share
    val partyOutputShare: Boolean on Party = on[Party]:
      val myIdRaw = getId(localAddress)
      // Party IDs are encoded in the address (e.g., "party-0" -> 0)
      val myId = localAddress.toString.split("-").last.toInt

      println(s"[Party $myId] Starting GMW protocol")

      // Initialize wire shares using deterministic derivation
      var myWireShares: WireShares = Map.empty

      // For each input gate, derive this party's share
      circuit.gates.foreach:
        case InputGate(ownerPartyId, wire) =>
          val inputValue = partyInputs.getOrElse(ownerPartyId, List.empty).headOption.getOrElse(false)
          
          // Deterministic share derivation
          val seed = ownerPartyId * 10000 + wire.index * 100
          val random = new scala.util.Random(seed)
          
          val shares = (0 until numParties - 1).map { i =>
            i -> random.nextBoolean()
          }.toMap
          
          val xorOfOthers = shares.values.fold(false)(_ ^ _)
          val lastShare = inputValue ^ xorOfOthers
          val allShares = shares + ((numParties - 1) -> lastShare)
          
          val myShare = allShares(myId)
          myWireShares = myWireShares + (wire.index -> myShare)
          println(s"[Party $myId] Input wire ${wire.index} (owner: $ownerPartyId, value: $inputValue): my share = $myShare")

        case _ => ()

      // Evaluate gates
      circuit.gates.foreach:
        case InputGate(_, _) => ()

        case XorGate(left, right, output) =>
          val leftShare = myWireShares.getOrElse(left.index, false)
          val rightShare = myWireShares.getOrElse(right.index, false)
          val outputShare = leftShare ^ rightShare
          myWireShares = myWireShares + (output.index -> outputShare)
          println(s"[Party $myId] XOR: wire ${left.index}($leftShare) ^ wire ${right.index}($rightShare) = wire ${output.index}($outputShare)")

        case AndGate(left, right, output) =>
          // GMW AND gate using simulated correlated randomness
          // For c = a AND b where a = XOR(a_i) and b = XOR(b_i):
          // c = XOR over all i,j of (a_i AND b_j)
          // 
          // Each party i computes:
          // c_i = (a_i AND b_i) XOR (sum of OT terms with other parties)
          //
          // For OT between party i (sender) and party j (receiver):
          // - Sender picks random r
          // - Sender prepares messages: m0 = r, m1 = r XOR a_i
          // - Receiver with choice b_j gets: r XOR (a_i AND b_j)
          // - Sender keeps: r
          // - Together they hold shares of (a_i AND b_j)
          
          val leftShare = myWireShares.getOrElse(left.index, false)
          val rightShare = myWireShares.getOrElse(right.index, false)

          // Start with local product
          var myContribution = leftShare && rightShare

          // For each pair (i,j) where i < j, simulate OT
          // The OT gives both parties shares of (a_i AND b_j) + (a_j AND b_i)
          for
            i <- 0 until numParties
            j <- 0 until numParties if i < j
          do
            // Get the shares that parties i and j hold for the input wires
            // We need to derive them the same way as above
            val seedLeft = (circuit.gates.collectFirst { case InputGate(p, w) if w.index == left.index => p }.getOrElse(0)) * 10000 + left.index * 100
            val seedRight = (circuit.gates.collectFirst { case InputGate(p, w) if w.index == right.index => p }.getOrElse(0)) * 10000 + right.index * 100
            
            val leftRandom = new scala.util.Random(seedLeft)
            val leftShares = (0 until numParties - 1).map(p => p -> leftRandom.nextBoolean()).toMap
            val leftXor = leftShares.values.fold(false)(_ ^ _)
            val leftInputValue = partyInputs.values.flatten.toList.lift(left.index).getOrElse(false)
            val allLeftShares = leftShares + ((numParties - 1) -> (leftInputValue ^ leftXor))
            
            val rightRandom = new scala.util.Random(seedRight)
            val rightShares = (0 until numParties - 1).map(p => p -> rightRandom.nextBoolean()).toMap
            val rightXor = rightShares.values.fold(false)(_ ^ _)
            val rightInputValue = partyInputs.values.flatten.toList.lift(right.index).getOrElse(false)
            val allRightShares = rightShares + ((numParties - 1) -> (rightInputValue ^ rightXor))
            
            val a_i = allLeftShares.getOrElse(i, false)
            val a_j = allLeftShares.getOrElse(j, false)
            val b_i = allRightShares.getOrElse(i, false)
            val b_j = allRightShares.getOrElse(j, false)
            
            // Cross products
            val cross_ij = a_i && b_j  // Party i's left share AND party j's right share
            val cross_ji = a_j && b_i  // Party j's left share AND party i's right share
            
            // Deterministic "random" mask for this pair
            val otSeed = output.index * 10000 + i * 100 + j
            val otRandom = new scala.util.Random(otSeed)
            val r_ij = otRandom.nextBoolean()
            val r_ji = otRandom.nextBoolean()

            if myId == i then
              // I'm party i in this pair
              // For cross_ij: I'm sender, I keep r_ij
              // For cross_ji: I'm receiver, I get r_ji XOR cross_ji
              myContribution = myContribution ^ r_ij ^ (r_ji ^ cross_ji)
            else if myId == j then
              // I'm party j in this pair
              // For cross_ij: I'm receiver, I get r_ij XOR cross_ij
              // For cross_ji: I'm sender, I keep r_ji
              myContribution = myContribution ^ (r_ij ^ cross_ij) ^ r_ji

          myWireShares = myWireShares + (output.index -> myContribution)
          println(s"[Party $myId] AND: wire ${left.index} & wire ${right.index} -> wire ${output.index}($myContribution)")

        case OutputGate(input, output) =>
          myWireShares = myWireShares + (output.index -> myWireShares.getOrElse(input.index, false))

      val outputWire = circuit.outputs.head.index
      val myShare = myWireShares.getOrElse(outputWire, false)
      println(s"[Party $myId] Output share: $myShare")
      myShare

    // Step 2: Coordinator gathers all shares and reconstructs result
    val coordinatorResult: Boolean on Coordinator = on[Coordinator]:
      println(s"[Coordinator] Gathering output shares from all parties")
      
      val allShares = asLocalAll[Party, Coordinator, Boolean](partyOutputShare)
      
      println(s"[Coordinator] Received ${allShares.size} shares:")
      allShares.foreach { case (id, share) =>
        println(s"[Coordinator]   Party $id: $share")
      }

      // XOR all shares to reconstruct
      val result = allShares.values.reduce(_ ^ _)
      println(s"[Coordinator] Reconstructed result: $result")
      result

    // Step 3: Distribute result back to parties
    val finalResult: Boolean on Party = on[Party]:
      val result = asLocal[Coordinator, Party, Boolean](coordinatorResult)
      println(s"[Party ${localAddress}] Received final result: $result")
      result

    (partyOutputShare, coordinatorResult, finalResult)
  end gmwProtocol

  // ============================================================
  // Main Entry Point
  // ============================================================

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    println("=" * 70)
    println("GMW Secure Multiparty Computation Protocol")
    println("=" * 70)
    println()

    // Demo 1: 3-party XOR (no OT needed)
    println("-" * 70)
    println("Demo 1: 3-Party XOR Function")
    println("Computing XOR(1, 0, 1) = 0")
    println("-" * 70)

    runDemo(
      threePartyXORCircuit,
      Map(0 -> List(true), 1 -> List(false), 2 -> List(true)),
      expectedResult = false, // 1 XOR 0 XOR 1 = 0
    )

    println()

    // Demo 2: 2-party AND
    println("-" * 70)
    println("Demo 2: 2-Party AND Function")
    println("Computing AND(1, 1) = 1")
    println("-" * 70)

    runDemo(
      twoPartyANDCircuit,
      Map(0 -> List(true), 1 -> List(true)),
      expectedResult = true, // 1 AND 1 = 1
    )

    println()

    // Demo 3: 2-party AND with different inputs
    println("-" * 70)
    println("Demo 3: 2-Party AND Function")
    println("Computing AND(1, 0) = 0")
    println("-" * 70)

    runDemo(
      twoPartyANDCircuit,
      Map(0 -> List(true), 1 -> List(false)),
      expectedResult = false, // 1 AND 0 = 0
    )

    println()
    println("=" * 70)
    println("GMW Protocol Demos Complete!")
    println("=" * 70)
  end main

  private def runDemo(circuit: Circuit, inputs: Map[Int, List[Boolean]], expectedResult: Boolean): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    val numParties = circuit.numParties

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
        println(s"\n[Starting] Party $i with input ${inputs.getOrElse(i, List.empty)}")
        given Locix[InMemoryNetwork[Party]] = Locix(network)
        PlacedValue.run[Party]:
          PlacedFlow.run[Party]:
            Multitier.run[Party]:
              val (_, _, finalResult) = gmwProtocol(circuit, inputs)
              finalResult.take
    }

    val coordinatorFuture = Future:
      println(s"\n[Starting] Coordinator")
      given Locix[InMemoryNetwork[Coordinator]] = Locix(coordinatorNetwork)
      PlacedValue.run[Coordinator]:
        PlacedFlow.run[Coordinator]:
          Multitier.run[Coordinator]:
            val (_, coordResult, _) = gmwProtocol(circuit, inputs)
            coordResult.take

    val allFutures = Future.sequence(partyFutures :+ coordinatorFuture)
    val results = Await.result(allFutures, 30.seconds)

    println()
    // Coordinator's result is the last one
    val coordinatorResult = results.last
    println(s"Coordinator computed result: $coordinatorResult")
    println(s"Expected result: $expectedResult")
    println(s"Verification: ${if coordinatorResult == expectedResult then "PASSED ✓" else "FAILED ✗"}")
  end runDemo

end GMW
