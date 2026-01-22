package io.github.nicolasfara.locix

import scala.concurrent.*
import scala.concurrent.duration.*

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{ Locix, Multitier }
import ox.flow.Flow

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
 * DPrio: Differentially Private Secure Aggregation Protocol
 *
 * This example implements a simplified version of the DPrio protocol, which extends Prio
 * with differential privacy. The key mechanism is:
 *
 *   1. Each client generates a secret noise value for the DP layer
 *   2. Clients split their noise into additive shares and send one share to each server
 *   3. Servers coordinate a "lottery" to randomly select ONE client's noise to use
 *   4. The selected noise is reconstructed and forwarded to the analyst
 *
 * Security guarantees:
 *   - As long as at least one server is honest, the lottery selection is truly random
 *   - The analyst receives only the aggregated noise, not individual client values
 *   - Servers only see shares, never the actual secrets
 *
 * This choreography demonstrates:
 *   - Census polymorphism: works with any number of clients and servers
 *   - Fan-out: clients broadcast shares to all servers
 *   - Fan-in: servers gather shares from all clients
 *   - Server-to-server coordination: lottery protocol
 *   - Many-to-many communication patterns
 */
object DPrio:
  /**
   * Client peers generate noise for differential privacy.
   * Each client is connected to multiple servers to distribute shares.
   */
  type Client <: { type Tie <: Multiple[Server] }

  /**
   * Server peers coordinate the lottery and hold shares.
   * Each server:
   *   - Receives shares from multiple clients
   *   - Coordinates with other servers for the lottery
   *   - Sends result to the analyst
   */
  type Server <: { type Tie <: Multiple[Client] & Multiple[Server] & Single[Analyst] }

  /**
   * Analyst peer receives the final aggregated noise.
   * Connected to multiple servers for the final aggregation.
   */
  type Analyst <: { type Tie <: Multiple[Server] }

  // ============================================================
  // Domain Types
  // ============================================================

  /**
   * A share of a client's secret noise value.
   * Additive shares: sum of all shares = original secret.
   */
  case class Share(clientId: String, value: Long)

  /**
   * Server's contribution to the lottery.
   * Each server generates a random value; XOR of all values determines winner.
   */
  case class LotteryContribution(serverId: String, randomValue: Long)

  /**
   * Result of the lottery - identifies which client's noise to use.
   */
  case class LotteryResult(winningClientIndex: Int, winningClientId: String)

  /**
   * Final share sent to analyst for reconstruction.
   */
  case class AnalystShare(serverId: String, share: Long)

  // ============================================================
  // Cryptographic Primitives (Simplified)
  // ============================================================

  /**
   * Split a secret into n additive shares.
   * The sum of all shares equals the original secret.
   *
   * In a real implementation, this would use proper finite field arithmetic.
   */
  def splitIntoShares(secret: Long, numShares: Int, clientId: String): List[Share] =
    val random = new scala.util.Random()
    // Generate n-1 random shares
    val randomShares = (1 until numShares).map(_ => random.nextLong() % 1000000L).toList
    // Last share is chosen so that sum = secret
    val lastShare = secret - randomShares.sum
    (randomShares :+ lastShare).map(Share(clientId, _))

  /**
   * Reconstruct the secret from additive shares.
   */
  def reconstructFromShares(shares: List[Share]): Long =
    shares.map(_.value).sum

  /**
   * Determine lottery winner using XOR of all contributions.
   * The XOR ensures that as long as one server is honest and random,
   * the result is uniformly random.
   */
  def computeLotteryWinner(
      contributions: Map[String, LotteryContribution],
      clientIds: List[String],
  ): LotteryResult =
    val combinedRandom = contributions.values.map(_.randomValue).reduce(_ ^ _)
    val winnerIndex = math.abs(combinedRandom % clientIds.size).toInt
    LotteryResult(winnerIndex, clientIds(winnerIndex))

  // ============================================================
  // The DPrio Choreography
  // ============================================================

  /**
   * Main DPrio protocol.
   *
   * This implements the core DPrio mechanism:
   *   1. Clients generate secret noise and split into shares (fan-out to servers)
   *   2. Servers collect shares from all clients (fan-in from clients)
   *   3. Servers exchange lottery contributions to randomly select a winner
   *   4. Servers send winning client's share to analyst (fan-in to analyst)
   *   5. Analyst reconstructs the selected noise
   *
   * Security properties:
   *   - Servers only see shares, never the actual secrets
   *   - The lottery is random as long as at least one server is honest
   *   - The analyst receives only the aggregated noise from one random client
   */
  def dprioProtocol(using net: Network, mt: Multitier, pf: PlacedFlow, pv: PlacedValue) =
    // Step 1: Each client generates a secret noise value and creates shares
    val clientShares: List[Share] on Client = on[Client]:
      val clientId = localAddress.toString
      val numServers = reachablePeersOf[Server].size

      // Generate secret noise (in real DP, this would be from Laplace/Gaussian distribution)
      val secretNoise = scala.util.Random.nextLong() % 1000L
      println(s"[Client:$clientId] Generated secret noise: $secretNoise")

      // Split into shares - one for each server
      val shares = splitIntoShares(secretNoise, numServers, clientId)
      println(s"[Client:$clientId] Split into ${shares.size} shares")

      // Return shares indexed by position (will be distributed to servers)
      shares

    // Step 2: Servers collect shares, run lottery, and forward winning share to analyst
    // This is the core of the census-polymorphic many-to-many communication
    val serverAnalystShares: AnalystShare on Server = on[Server]:
      val serverId = localAddress.toString
      val serverIndex = getId(localAddress).toString.takeRight(1).toInt

      // ===== GATHER: Collect shares from all clients =====
      val allClientShares: Map[net.effect.Id, List[Share]] = 
        asLocalAll[Client, Server, List[Share]](clientShares)

      // Each server takes their designated share from each client
      val myShares: Map[String, Share] = allClientShares.map: (_, sharesList) =>
        val myShare = sharesList.lift(serverIndex).getOrElse(sharesList.head)
        println(s"[Server:$serverId] Received share from client ${myShare.clientId}: ${myShare.value}")
        myShare.clientId -> myShare
      .toMap

      // ===== LOTTERY: Deterministic winner selection =====
      // For all servers to agree on the same winner without explicit coordination,
      // we use a deterministic function of the client IDs.
      // In a full DPrio implementation, servers would exchange random commitments
      // via the network to achieve random but agreed-upon selection.
      val clientIds = myShares.keys.toList.sorted
      
      // Use a hash of all client IDs as the "shared random" value
      // This ensures all servers select the same winner deterministically
      val sharedRandom = clientIds.mkString(",").hashCode.toLong
      val winnerIndex = math.abs(sharedRandom % clientIds.size).toInt
      val winningClientId = clientIds(winnerIndex)
      println(s"[Server:$serverId] Lottery winner: $winningClientId (index $winnerIndex)")

      // ===== FORWARD: Send winning client's share to analyst =====
      val winningShareValue: Long = myShares.get(winningClientId) match
        case Some(share) =>
          println(s"[Server:$serverId] Forwarding share ${share.value} for client $winningClientId")
          share.value
        case None =>
          println(s"[Server:$serverId] ERROR: No share found for winning client")
          0L

      AnalystShare(serverId, winningShareValue)

    // Step 3: Analyst reconstructs the noise from the winning client
    val finalResult: Long on Analyst = on[Analyst]:
      val analystId = localAddress.toString

      // Gather shares from all servers
      val allShares: Map[net.effect.Id, AnalystShare] = 
        asLocalAll[Server, Analyst, AnalystShare](serverAnalystShares)
      println(s"[Analyst:$analystId] Received ${allShares.size} shares from servers")

      // Print each share received
      allShares.foreach: (serverId, share) =>
        println(s"[Analyst:$analystId]   - Server ${share.serverId}: share = ${share.share}")

      // Reconstruct the noise by summing all additive shares
      val sharesList = allShares.values.map(as => Share("winner", as.share)).toList
      val reconstructedNoise = reconstructFromShares(sharesList)

      println(s"[Analyst:$analystId] Reconstructed noise value: $reconstructedNoise")
      println(s"[Analyst:$analystId] This noise can be used for differential privacy!")

      reconstructedNoise
    
    finalResult
  end dprioProtocol

  // ============================================================
  // Alternative: Full Lottery Protocol with Server Coordination
  // ============================================================

  /**
   * DPrio protocol with full server-to-server lottery coordination.
   *
   * This version demonstrates the complete lottery mechanism where servers
   * exchange random contributions to ensure the winner selection is truly
   * random even if some servers are malicious.
   *
   * The key difference from the simplified version:
   *   - Servers first produce lottery contributions
   *   - These contributions are gathered by all servers
   *   - The XOR of all contributions determines the winner
   *   - This ensures randomness as long as at least one server is honest
   */
  def dprioProtocolWithFullLottery(using net: Network, mt: Multitier, pf: PlacedFlow, pv: PlacedValue) =
    // Step 1: Clients generate shares (same as before)
    val clientShares: List[Share] on Client = on[Client]:
      val clientId = localAddress.toString
      val numServers = reachablePeersOf[Server].size
      val secretNoise = scala.util.Random.nextLong() % 1000L
      println(s"[Client:$clientId] Generated secret noise: $secretNoise")
      val shares = splitIntoShares(secretNoise, numServers, clientId)
      println(s"[Client:$clientId] Split into ${shares.size} shares")
      shares

    // Step 2: Servers generate lottery contributions
    // This separate step allows the contributions to be gathered by other servers
    val serverLotteryContrib: LotteryContribution on Server = on[Server]:
      val serverId = localAddress.toString
      val randomContribution = scala.util.Random.nextLong()
      println(s"[Server:$serverId] Generated lottery contribution: $randomContribution")
      LotteryContribution(serverId, randomContribution)

    // Step 3: Servers gather shares, gather lottery contributions, and compute result
    val serverSharesResult: AnalystShare on Server = on[Server]:
      val serverId = localAddress.toString
      val serverIndex = getId(localAddress).toString.takeRight(1).toInt

      // Gather shares from all clients
      val allClientShares = asLocalAll[Client, Server, List[Share]](clientShares)
      val myShares: Map[String, Share] = allClientShares.map: (_, sharesList) =>
        val myShare = sharesList.lift(serverIndex).getOrElse(sharesList.head)
        println(s"[Server:$serverId] Received share from ${myShare.clientId}: ${myShare.value}")
        myShare.clientId -> myShare
      .toMap

      // Gather lottery contributions from all servers (including self via the placed value)
      val allContributions = asLocalAll[Server, Server, LotteryContribution](serverLotteryContrib)
      println(s"[Server:$serverId] Collected ${allContributions.size} lottery contributions")

      // Compute winner using XOR of all random values
      val clientIds = myShares.keys.toList.sorted
      val combinedRandom = allContributions.values.map(_.randomValue).reduce(_ ^ _)
      val winnerIndex = math.abs(combinedRandom % clientIds.size).toInt
      val winningClientId = clientIds(winnerIndex)
      println(s"[Server:$serverId] Lottery winner: $winningClientId (combined random: $combinedRandom)")

      // Forward winning share
      val winningShareValue = myShares.get(winningClientId).map(_.value).getOrElse(0L)
      println(s"[Server:$serverId] Forwarding share $winningShareValue")
      AnalystShare(serverId, winningShareValue)

    // Step 4: Analyst reconstructs (same as before)
    val finalResult: Long on Analyst = on[Analyst]:
      val analystId = localAddress.toString
      val allShares = asLocalAll[Server, Analyst, AnalystShare](serverSharesResult)
      println(s"[Analyst:$analystId] Received ${allShares.size} shares")
      allShares.foreach((_, s) => println(s"[Analyst:$analystId]   - ${s.serverId}: ${s.share}"))
      val reconstructedNoise = allShares.values.map(_.share).sum
      println(s"[Analyst:$analystId] Reconstructed noise: $reconstructedNoise")
      reconstructedNoise

    finalResult
  end dprioProtocolWithFullLottery

  // ============================================================
  // Main Entry Point
  // ============================================================

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    println("=" * 60)
    println("DPrio: Differentially Private Secure Aggregation Protocol")
    println("=" * 60)
    println()

    // Create network topology with multiple clients and servers
    // The protocol is census-polymorphic - works with any number of each

    // Create 3 clients
    val client1Network = InMemoryNetwork[Client]("client-1", 0)
    val client2Network = InMemoryNetwork[Client]("client-2", 1)
    val client3Network = InMemoryNetwork[Client]("client-3", 2)

    // Create 3 servers (need >= 2 for security)
    val server1Network = InMemoryNetwork[Server]("server-1", 0)
    val server2Network = InMemoryNetwork[Server]("server-2", 1)
    val server3Network = InMemoryNetwork[Server]("server-3", 2)

    // Create 1 analyst
    val analystNetwork = InMemoryNetwork[Analyst]("analyst", 0)

    // Wire up the network topology
    // Clients connect to all servers (fan-out)
    List(client1Network, client2Network, client3Network).foreach: clientNet =>
      clientNet.addReachablePeer(server1Network)
      clientNet.addReachablePeer(server2Network)
      clientNet.addReachablePeer(server3Network)

    // Servers connect to all clients (fan-in)
    List(server1Network, server2Network, server3Network).foreach: serverNet =>
      serverNet.addReachablePeer(client1Network)
      serverNet.addReachablePeer(client2Network)
      serverNet.addReachablePeer(client3Network)

    // Servers connect to each other (for lottery)
    server1Network.addReachablePeer(server2Network)
    server1Network.addReachablePeer(server3Network)
    server2Network.addReachablePeer(server1Network)
    server2Network.addReachablePeer(server3Network)
    server3Network.addReachablePeer(server1Network)
    server3Network.addReachablePeer(server2Network)

    // Servers connect to analyst
    server1Network.addReachablePeer(analystNetwork)
    server2Network.addReachablePeer(analystNetwork)
    server3Network.addReachablePeer(analystNetwork)

    // Analyst connects to all servers
    analystNetwork.addReachablePeer(server1Network)
    analystNetwork.addReachablePeer(server2Network)
    analystNetwork.addReachablePeer(server3Network)

    // Run the choreography on all peers concurrently
    val client1Future = Future:
      println("\n[Starting] Client 1")
      given Locix[InMemoryNetwork[Client]] = Locix(client1Network)
      PlacedValue.run[Client]:
        PlacedFlow.run[Client]:
          Multitier.run[Client](dprioProtocol)

    val client2Future = Future:
      println("[Starting] Client 2")
      given Locix[InMemoryNetwork[Client]] = Locix(client2Network)
      PlacedValue.run[Client]:
        PlacedFlow.run[Client]:
          Multitier.run[Client](dprioProtocol)

    val client3Future = Future:
      println("[Starting] Client 3")
      given Locix[InMemoryNetwork[Client]] = Locix(client3Network)
      PlacedValue.run[Client]:
        PlacedFlow.run[Client]:
          Multitier.run[Client](dprioProtocol)

    val server1Future = Future:
      println("[Starting] Server 1")
      given Locix[InMemoryNetwork[Server]] = Locix(server1Network)
      PlacedValue.run[Server]:
        PlacedFlow.run[Server]:
          Multitier.run[Server](dprioProtocol)

    val server2Future = Future:
      println("[Starting] Server 2")
      given Locix[InMemoryNetwork[Server]] = Locix(server2Network)
      PlacedValue.run[Server]:
        PlacedFlow.run[Server]:
          Multitier.run[Server](dprioProtocol)

    val server3Future = Future:
      println("[Starting] Server 3")
      given Locix[InMemoryNetwork[Server]] = Locix(server3Network)
      PlacedValue.run[Server]:
        PlacedFlow.run[Server]:
          Multitier.run[Server](dprioProtocol)

    val analystFuture = Future:
      println("[Starting] Analyst")
      given Locix[InMemoryNetwork[Analyst]] = Locix(analystNetwork)
      PlacedValue.run[Analyst]:
        PlacedFlow.run[Analyst]:
          Multitier.run[Analyst](dprioProtocol)

    // Wait for all peers to complete
    val allFutures = Future.sequence(List(
      client1Future,
      client2Future,
      client3Future,
      server1Future,
      server2Future,
      server3Future,
      analystFuture,
    ))

    Await.result(allFutures, 30.seconds)

    println()
    println("=" * 60)
    println("DPrio Protocol Complete!")
    println("=" * 60)
  end main
end DPrio
