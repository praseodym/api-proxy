package nl.praseodym

import spray.can.server.SprayCanHttpServerApp
import akka.actor.Props

object Main extends App with SprayCanHttpServerApp {

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[ApiService])

  // create a new HttpServer using our handler and tell it where to bind to
  newHttpServer(handler) ! Bind(interface = "localhost", port = 8080)

}