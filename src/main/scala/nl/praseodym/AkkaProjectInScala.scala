package nl.praseodym

import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.util.{Success, Failure}
import scala.concurrent.Future
import akka.actor._
import spray.can.client.HttpClient
import spray.io._
import spray.http._
import HttpMethods._
import spray.client.HttpConduit
import scala.language.postfixOps

case object Tick
case object Get

class Counter extends Actor {
  var count = 0

  def receive = {
    case Tick => count += 1
    case Get => sender ! count
  }
}

object AkkaProjectInScala extends App {

  import system.dispatcher
  import system.log

  val system = ActorSystem("AkkaProjectInScala")

  val counter = system.actorOf(Props[Counter])

  val ioBridge = IOExtension(system).ioBridge()
  val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

  val conduit = system.actorOf(
//    props = Props(new HttpConduit(httpClient, "ch.tudelft.nl", 443, sslEnabled=true)),
    props = Props(new HttpConduit(httpClient, "localhost", 57256, sslEnabled=true)),
//    props = Props(new HttpConduit(httpClient, "www.google.nl")),
    name = "http-conduit"
  )

  counter ! Tick
  counter ! Tick
  counter ! Tick

  implicit val timeout = Timeout(5 seconds)

  (counter ? Get) onSuccess {
    case count => println("Count is " + count)
  }

  log.info("TU API call ...")

  val pipeline = HttpConduit.sendReceive(conduit)
  val response: Future[HttpResponse] = pipeline(HttpRequest(method = GET, uri="/v0/gebouwen?computerlokaal=true"))
  response onComplete {
    case Success(response) =>
      log.info(
        """|Response for GET request:
          |status : {}
          |headers: {}
          |body   : {}""".stripMargin,
        response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
      )
      system.shutdown()

    case Failure(error) =>
      log.error(error, "Couldn't get from API.")
      system.shutdown()
  }

}
