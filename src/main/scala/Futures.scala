import sun.reflect.misc.FieldUtil

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Futures extends App {
  def productStock(product: String): Future[Int] = Future {
    println("Fetching product stock...")
    10
  }

  ////////////////////////////////////////////
  // BLOCKING FUTURES - highly discouraged
  /////////////////////////////////////////

  // using Await.result
  println("\n1: Blocking future result")
  val tShirtStock = Await.result(productStock("t-shirts"), 5 seconds)
  println("T-shirt stock: " + tShirtStock)

  // using Await.ready
  val sStock = productStock("shirts")
  Await.ready(sStock, 5 seconds)
  val shirtStock = sStock.value match {
    case Some(value) => s"Shirt stock is: $value" // value: Try[Int]
    case None => "Shirts are out"
  }
  println(shirtStock)

  ////////////////////////////////////////////
  // COMPOSING FUTURES - NON-BLOCKING TASKS
  /////////////////////////////////////////

  // using onComplete -> returns a callback for either Success or a Failure
  println("\n2: Non blocking future result")
  productStock("shoes").onComplete {
    case Success(value) => println(s"Shoes stock: $value")
    case Failure(ex) => println(s"Some errors fetching shoes stock, ${ex.getMessage}")
  }
  Thread.sleep(3000)

  // SEQUENCING MULTIPLE FUTURES IN ORDER
  def buyProducts(amount: Int): Future[Boolean] = Future {
    println(s"Buying $amount of products")
    Thread.sleep(3000)
    if (amount > 0) true else false
  }

  // chaining futures using flatMap
  println("\n3: Chaining Futures using flatMap")
  val buyingHats = productStock("hats").flatMap(hatStock => buyProducts(hatStock))
  val isSuccess = Await.result(buyingHats, 4 seconds)
  println(s"Buying hats was successful = $isSuccess")

  // chaining futures using for comprehension
  println("\n4: Chaining Futures using for comprehension")
  for {
    capStock <- productStock("caps")
    result   <- buyProducts(capStock)
  } yield println(s"Buying caps was successful = $result")


  //////////////////////////////////////////
  // Future Option with for comprehension
  ///////////////////////////////////////
  println("4 a. : Method which returns a Future Option")
  def boxersStock(boxer: String): Future[Option[Int]] = Future {
    // Thread.sleep(2000)
    if (boxer == "hugo boss") Some(10) else None
  }

  for {
    boxerStock: Option[Int] <- boxersStock("hugo boss")
    result: Boolean         <- buyProducts(boxerStock.getOrElse(0))
  } yield println(s"Buying boxers was successful = $result")
  Thread.sleep(3000)


  ////////////////////////////
  // FUTURE TRANSFORMATIONS
  /////////////////////////

  // Future.sequence
  // sequence interleaves operations and therefore the order is never guaranteed
  println("\n5: Method for processing payments and returns a Future[Unit]")
  def processPayment(): Future[Unit] = Future {
    println("Processing payment... wait 1 second")
    Thread.sleep(1000)
  }

  val futureOperations: List[Future[Any]] = List(
    productStock("hats"),
    buyProducts(10),
    processPayment()
  )

  val futureSequenceResults: Future[List[Any]] = Future.sequence(futureOperations)
  futureSequenceResults.onComplete {
    case Success(value) => println(s"Sequence result: $value")
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

  // Future.traverse
  // traverse is similar to Future.sequence albeit it allows you to apply a function over the future operations
  println(s"\n6: Future traverse operations")
  val futureOps: List[Future[Option[Int]]] = List(
    boxersStock("sandals"),   // Future[Int]
    boxersStock("hugo boss"), // Future[Option[Int]]
    boxersStock("hoodies")    // Future[Int]
  )

  // transform all return types to Future[Int]
  val futureTraverseResults: Future[List[Int]] = Future.traverse(futureOps){ futureSomeQty =>
    futureSomeQty.map(someQty => someQty.getOrElse(0))
  }
  futureTraverseResults.onComplete {
    case Success(value) => println(s"Traverse result: $value") // List(0, 10, 0)
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

  // Future.foldLeft
  // foldLeft takes an associative binary function as a parameter and will use it to collapse elements from the collection
  println(s"\n7: Aggregate Future results using foldLeft")
  val futOps: List[Future[Option[Int]]] = List(
    boxersStock("björn borg"),
    boxersStock("hugo boss"),
    boxersStock("calvin klein"),
    boxersStock("hugo boss")
  )

  // aggregate boxers quantities
  val futFoldLeftResult: Future[Int] = Future.foldLeft(futOps)(0) { case (acc, someQty) => // NOTE: acc: Int
    acc + someQty.getOrElse(0)
  }
  futFoldLeftResult.onComplete {
    case Success(value) => println(s"FoldLeft result: $value") // 20 (0 + 10 + 0 + 10)
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

  // Future.reduceLeft
  // reduceLeft is similar to foldLeft albeit it does not take a default value
  println(s"\n8: Aggregate Future results using reduceLeft")
  val futReduceLeftResult = Future.reduceLeft(futOps) { case (acc, someQty) => // NOTE: acc: Option[Int] as we dont provide default value
    acc.map(qty => qty + someQty.getOrElse(0))
  }
  futReduceLeftResult.onComplete {
    case Success(value) => println(s"ReduceLeft result: $value") // None
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

  // Future.firstCompletedOf
  // firstCompletedOf returns the first future that completes
  println(s"\n9: Call Future.firstCompletedOf to get the results of the first future that completes")
  val futFirstCompletedOfResult: Future[Option[Int]] = Future.firstCompletedOf(futOps)
  futFirstCompletedOfResult.onComplete {
    case Success(value) => println(s"First completed future: $value") // Some(10)
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

  // Future.zip
  // zip creates a new Future whose return type will be a tuple holding the return types of the the two futures
  def boxersPrice(price: Double): Future[Double] = Future.successful(price)

  println(s"\n10: Combine future results using Zip")
  val boxersStockAndPrice = boxersStock("hugo boss").zip(boxersPrice(15.99))
  boxersStockAndPrice.onComplete {
    case Success(value) => println(s"Zip result: $value") // (Some(10),15.99)
    case Failure(ex) => println(s"Error processing future operations, ${ex.getMessage}")
  }

}
