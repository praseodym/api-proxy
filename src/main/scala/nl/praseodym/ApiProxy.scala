package nl.praseodym

import spray.caching.{LruCache, Cache}
import scala.util.{Success, Failure}
import scala.concurrent.Future
import akka.actor._
import spray.can.client.HttpClient
import spray.io._
import spray.http._
import HttpMethods._
import spray.client.HttpConduit

object ApiProxy {
  import system.dispatcher
  import system.log

  val system = ActorSystem("ApiProxy")

  // and a Cache for its result type
  val cache: Cache[HttpResponse] = LruCache()

  def getCached(uri: String): Future[HttpResponse] = cache.fromFuture(uri) {
    getFromApi(uri)
  }

  def getFromApi(uri: String): Future[HttpResponse] = {
    val system = ActorSystem("ApiProject")
    val ioBridge = IOExtension(system).ioBridge()
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    val conduit = system.actorOf(
      //    props = Props(new HttpConduit(httpClient, "api.tudelft.nl", 443, sslEnabled=true)),
      props = Props(new HttpConduit(httpClient, "localhost", 57256, sslEnabled=true)),
      name = "http-conduit"
    )
    val pipeline = HttpConduit.sendReceive(conduit)
    val response = pipeline(HttpRequest(method = GET, uri=uri))
    response onComplete {
      case Success(response) =>
//        log.info(
//          """|Response for GET request:
//            |status : {}
//            |headers: {}
//            |body   : {}""".stripMargin,
//          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
//        )
        log.info("Response: {} {}", response.status.value, uri)

      case Failure(error) =>
        log.error(error, "Couldn't get from API.")
    }
    response
  }
}
