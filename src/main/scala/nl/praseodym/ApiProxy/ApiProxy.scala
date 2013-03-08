package nl.praseodym.ApiProxy

import spray.caching.{LruCache, Cache}
import scala.concurrent.Future
import spray.client.HttpClient
import spray.client.pipelining._
import scala.language.postfixOps
import util.Success
import util.Failure
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor._
import spray.util._
import spray.http._
import MediaTypes._
import scala.language.postfixOps
import spray.http.HttpHeaders._
import spray.http.HttpCharsets._
import spray.http.HttpResponse
import spray.http.HttpHeaders.RawHeader
import scala.Some
import spray.http.CacheDirectives._

object ApiProxy {
  import Main.system.dispatcher
  import Main.system.log

  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Headers", "X-Requested-With"), `Cache-Control`(`max-age`(30)))
  val cache: Cache[HttpResponse] = LruCache() // (timeToLive = 5 minutes)

  def getCached(uri: String): Future[HttpResponse] = cache.fromFuture(uri) {
    getAndRewrite(uri)
  }

  def refreshCached(uri: String) =
    getAndRewrite(uri).onSuccess {
      case response: HttpResponse => {
        cache.remove(uri)
        cache(uri)(response)
      }
    }

  def getAndRewrite(uri: String): Future[HttpResponse] = {
    getFromApi(uri) map { case x =>
        val contentType = x.headers.mapFind {
          case `Content-Type`(t) => Some(t)
          case _ => None
        }.getOrElse(ContentType(`text/plain`, `US-ASCII`)) // RFC 2046 section 5.1
        val headers = `Content-Type`(contentType) :: corsHeaders
        x.withHeaders(headers)
    }
  }

  implicit val timeout: Timeout = 22 seconds
  val httpClient = Main.system.actorOf(Props(new HttpClient), "http-client")

  def getFromApi(uri: String): Future[HttpResponse] = {
    val pipeline = sendReceive(httpClient)
    val response = pipeline {
      Get("https://api.tudelft.nl" + uri)
    }
    response onComplete {
      case Success(response) =>
        log.debug(
          """|Response for GET request {}:
            |status : {}
            |headers: {}
            |body   : {}""".stripMargin,
          uri, response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
        )
        log.info("Response: {} {}", response.status.value, uri)

      case Failure(error) =>
        log.error(error, "Couldn't get from API [{}]", uri)
    }
    response
  }
}
