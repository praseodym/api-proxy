package nl.praseodym

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.io.{IOBridge, IOExtension}
import spray.can.server.HttpServer
import spray.util._
import spray.http._
import HttpMethods._
import MediaTypes._
import scala.language.postfixOps
import ApiProxy._
import java.lang.Runnable
import spray.http.HttpHeaders._
import spray.http.HttpHeaders.RawHeader
import scala.Some
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import scala.Some
import spray.http.HttpResponse
import spray.http.HttpHeaders.RawHeader
import scala.Some

class ApiService extends Actor with SprayActorLogging {
  implicit val timeout = Timeout(2 seconds)
  
  val cachePrimerRunnable = new Runnable { def run() = primeCache }
  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Headers", "X-Requested-With"))

  def receive = {
    case HttpRequest(GET, "/server-stats", _, _, _) =>
      val client = sender
      (context.actorFor("../http-server") ? HttpServer.GetStats).onSuccess {
        case x: HttpServer.Stats => client ! statsPresentation(x)
      }

    case HttpRequest(GET, "/io-stats", _, _, _) =>
      val client = sender
      (IOExtension(context.system).ioBridge() ? IOBridge.GetStats).onSuccess {
        case IOBridge.StatsMap(map) => client ! statsPresentation(map)
      }

    case HttpRequest(GET, "/prime-cache", _, _, _) =>
      sender ! HttpResponse(entity = "Priming cache ...")
      context.system.scheduler.scheduleOnce(1 seconds, cachePrimerRunnable)

    case HttpRequest(GET, "/keep-warm", _, _, _) =>
      sender ! HttpResponse(entity = "Alright.")
      context.system.scheduler.schedule(1 seconds, 4 minutes, cachePrimerRunnable)

    case HttpRequest(GET, uri, _, _, _) if uri.startsWith("/v0") =>
      log.info("Proxy request: {}", uri)
      val client = sender
      getCached(uri).onSuccess {
        case x: HttpResponse =>
          val contentType = x.headers.mapFind {
            case `Content-Type`(t) => Some(t)
            case _ => None
          }.getOrElse(ContentType(`text/plain`, `US-ASCII`)) // RFC 2046 section 5.1
          val headers = `Content-Type`(contentType) :: corsHeaders
          client ! x.withHeaders(headers)
      }

    case HttpRequest(OPTIONS, uri, _, _, _) if uri.startsWith("/v0") =>
      log.info("OPTIONS request: {}", uri)
      sender ! HttpResponse(headers=corsHeaders)

    case HttpRequest(GET, "/stop", _, _, _) =>
      sender ! HttpResponse(entity = "Shutting down in 1 second ...")
      context.system.scheduler.scheduleOnce(1 seconds, new Runnable { def run() { context.system.shutdown() } })

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")
  }

  def primeCache = {
    log.info("Priming cache")
    List("gebouwen", "faculteiten", "opleidingen/CiTG", "opleidingen/EWI", "opleidingen/BK",
      "opleidingen/TNW", "opleidingen/3mE").map(x => refreshCached("/v0/" + x))
  }

  def statsPresentation(s: HttpServer.Stats) = HttpResponse(
    entity = HttpBody(`text/html`,
      <html>
        <body>
          <h1>HttpServer Stats</h1>
          <table>
            <tr><td>uptime:</td><td>{s.uptime.formatHMS}</td></tr>
            <tr><td>totalRequests:</td><td>{s.totalRequests}</td></tr>
            <tr><td>openRequests:</td><td>{s.openRequests}</td></tr>
            <tr><td>maxOpenRequests:</td><td>{s.maxOpenRequests}</td></tr>
            <tr><td>totalConnections:</td><td>{s.totalConnections}</td></tr>
            <tr><td>openConnections:</td><td>{s.openConnections}</td></tr>
            <tr><td>maxOpenConnections:</td><td>{s.maxOpenConnections}</td></tr>
            <tr><td>requestTimeouts:</td><td>{s.requestTimeouts}</td></tr>
            <tr><td>idleTimeouts:</td><td>{s.idleTimeouts}</td></tr>
          </table>
        </body>
      </html>.toString
    )
  )

  def statsPresentation(map: Map[ActorRef, IOBridge.Stats]) = HttpResponse(
    entity = HttpBody(`text/html`,
      <html>
        <body>
          <h1>IOBridge Stats</h1>
          <table>
            {
            def extractData(t: (ActorRef, IOBridge.Stats)) = t._1.path.elements.last :: t._2.productIterator.toList
            val data = map.toSeq.map(extractData).sortBy(_.head.toString).transpose
            val headers = Seq("IOBridge", "uptime", "bytesRead", "bytesWritten", "connectionsOpened", "commandsExecuted")
            headers.zip(data).map { case (header, items) =>
              <tr><td>{header}:</td>{items.map(x => <td>{x}</td>)}</tr>
            }
            }
          </table>
        </body>
      </html>.toString
    )
  )
}