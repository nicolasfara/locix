package io.github.nicolasfara.locix

import scala.concurrent.Await
import scala.concurrent.Future

import io.github.nicolasfara.locix.network.InMemoryNetwork
import io.github.nicolasfara.locix.{ Choreography, Locix }

import Choreography.*
import network.Network
import network.Network.*
import placement.Peers.Quantifier.Single
import placement.Peers.given
import placement.PlacedValue
import placement.PlacedValue.*

/**
 * Classical Book Store Protocol
 *
 * This example demonstrates a choreographic programming bookstore example,
 * where a buyer requests a book quote from a seller and receives a response.
 *
 * Protocol:
 *   1. Buyer sends a book request to the Seller
 *   2. Seller looks up the price and sends confirmation back to the Buyer
 *
 * This showcases:
 *   - Two-party choreography
 *   - Bidirectional communication
 *   - Point-to-point communication using `comm[Sender, Receiver]`
 */
object BookStore:

  // Peer type definitions with cardinality constraints
  // Buyer is tied to a single Seller
  type Buyer <: { type Tie <: Single[Seller] }

  // Seller is tied to a single Buyer
  type Seller <: { type Tie <: Single[Buyer] }

  // Domain types for the protocol
  case class BookOrder(title: String, budget: Double, deliveryAddress: String)
  case class OrderResult(success: Boolean, message: String)

  // Simulated book catalog
  val bookCatalog: Map[String, Double] = Map(
    "Deep Learning with Python" -> 45.99,
    "Types and Programming Languages" -> 89.99,
    "Structure and Interpretation of Computer Programs" -> 55.00,
    "The Art of Computer Programming" -> 199.99,
    "Introduction to Algorithms" -> 79.99,
  )

  /**
   * The Book Store Protocol
   *
   * Demonstrates a choreographic pattern where a buyer sends an order
   * to a seller and receives a confirmation.
   */
  def bookStoreProtocol(using Network, Choreography, PlacedValue) =
    // Step 1: Buyer creates an order with title, budget, and delivery address
    val order = on[Buyer]:
      val bookOrder = BookOrder(
        title = "Types and Programming Languages",
        budget = 100.0,
        deliveryAddress = "123 Functional Lane, Lambda City",
      )
      println(s"[$localAddress] Buyer placing order for '${bookOrder.title}' with budget $$${bookOrder.budget}")
      bookOrder

    // Step 2: Send order from Buyer to Seller
    val orderAtSeller = comm[Buyer, Seller](order)

    // Step 3: Seller processes the order and prepares result
    val result = on[Seller]:
      val receivedOrder = orderAtSeller.take
      val price = bookCatalog.getOrElse(receivedOrder.title, 0.0)
      println(s"[$localAddress] Seller received order for '${receivedOrder.title}', price: $$${price}")

      if price > 0 && price <= receivedOrder.budget then
        val orderId = java.util.UUID.randomUUID().toString.take(8)
        println(s"[$localAddress] Order $orderId accepted! Shipping to ${receivedOrder.deliveryAddress}")
        OrderResult(true, s"Order $orderId confirmed! '${receivedOrder.title}' ($$${price}) shipping to ${receivedOrder.deliveryAddress}")
      else if price > receivedOrder.budget then
        println(s"[$localAddress] Order declined - price $$${price} exceeds budget $$${receivedOrder.budget}")
        OrderResult(false, s"Order declined: '${receivedOrder.title}' costs $$${price}, exceeds budget $$${receivedOrder.budget}")
      else
        println(s"[$localAddress] Book not found: '${receivedOrder.title}'")
        OrderResult(false, s"Book not found: '${receivedOrder.title}'")

    // Step 4: Seller sends result back to Buyer
    val resultAtBuyer = comm[Seller, Buyer](result)

    // Step 5: Buyer receives and displays result
    on[Buyer]:
      val orderResult = resultAtBuyer.take
      if orderResult.success then
        println(s"[$localAddress] SUCCESS: ${orderResult.message}")
      else
        println(s"[$localAddress] FAILED: ${orderResult.message}")
      orderResult
  end bookStoreProtocol

  def main(args: Array[String]): Unit =
    import scala.concurrent.ExecutionContext.Implicits.global

    // Create network nodes for each participant
    val buyerNetwork = InMemoryNetwork[Buyer]("buyer-address", 1)
    val sellerNetwork = InMemoryNetwork[Seller]("seller-address", 2)

    // Set up bidirectional connectivity between Buyer and Seller
    buyerNetwork.addReachablePeer(sellerNetwork)
    sellerNetwork.addReachablePeer(buyerNetwork)

    println("=== Book Store Protocol ===\n")

    // Run each participant concurrently
    val buyerFuture = Future:
      println("Starting Buyer")
      given Locix[InMemoryNetwork[Buyer]] = Locix(buyerNetwork)
      PlacedValue.run[Buyer]:
        Choreography.run[Buyer](bookStoreProtocol)

    val sellerFuture = Future:
      println("Starting Seller")
      given Locix[InMemoryNetwork[Seller]] = Locix(sellerNetwork)
      PlacedValue.run[Seller]:
        Choreography.run[Seller](bookStoreProtocol)

    val complete = Future.sequence(List(buyerFuture, sellerFuture))
    Await.result(complete, scala.concurrent.duration.Duration.Inf)

    println("\n=== Book Store Protocol Completed ===")
  end main
end BookStore
