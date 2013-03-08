package nl.praseodym

import spray.caching.{LruCache, Cache}
import scala.util.{Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import spray.client.HttpClient
import akka.util.Timeout
import spray.client.pipelining._
import spray.http._
import scala.language.postfixOps

object ApiProxy {
  import system.dispatcher
  import system.log

  val system = ActorSystem("ApiProxy")

//  val cache: Cache[HttpResponse] = LruCache(timeToLive = 5 minutes)
  val cache: Cache[HttpResponse] = LruCache()

  def getCached(uri: String): Future[HttpResponse] = cache.fromFuture(uri) {
    getFromApi(uri)
  }

  def refreshCached(uri: String) =
    getFromApi(uri).onSuccess {
      case response: HttpResponse => {
        cache.remove(uri)
        cache(uri)(response)
      }
    }

  implicit val timeout: Timeout = 22 seconds
  val httpClient = system.actorOf(Props(new HttpClient), "http-client")

  def getFromApi(uri: String): Future[HttpResponse] = {
    val pipeline = sendReceive(httpClient)
    val response = pipeline {
      Get("https://127.0.0.1:57256" + uri)
    }
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
        log.error(error, "Couldn't get from API [{}]", uri)
    }
    response
  }
}
