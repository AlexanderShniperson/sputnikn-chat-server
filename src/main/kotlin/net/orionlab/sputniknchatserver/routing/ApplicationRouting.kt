package net.orionlab.sputniknchatserver.routing

import akka.actor.ActorSystem
import net.orionlab.sputniknchatserver.actor.ClientManagerActor
import net.orionlab.sputniknchatserver.actor.RoomManagerActor
import net.orionlab.sputniknchatserver.db.DbRepositoryImpl
import com.fasterxml.jackson.databind.SerializationFeature
import com.zaxxer.hikari.HikariConfig
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*

@Suppress("unused", "UNUSED_VARIABLE")
fun Application.appModule(dbConfig: HikariConfig, mediaPath: String) {
    val dbRepository = DbRepositoryImpl(dbConfig)
    val actorSystem = ActorSystem.create("ChatServer")
    val clientManager = actorSystem.actorOf(ClientManagerActor.props(), ClientManagerActor.actorName())
    val roomManager = actorSystem.actorOf(RoomManagerActor.props(dbRepository), RoomManagerActor.actorName())
    environment.monitor.subscribe(ApplicationStopped) {
        actorSystem.terminate()
        dbRepository.close()
        log.debug("Time to clean up")
    }
    install(DefaultHeaders) {
        header(HttpHeaders.Server, "Microsoft Windows 3.1")
    }
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.AccessControlAllowHeaders)
        header(HttpHeaders.ContentType)
        header(HttpHeaders.AccessControlAllowOrigin)
        anyHost()
    }
    install(StatusPages) {
        exception<Throwable> { e ->
            log.error("", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                e.message ?: "Internal server error"
            )
        }
    }
    /*install(CallLogging) {
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val headers = call.request.headers.entries().map {
                it.key to it.value
            }.joinToString("\n")
            """
                $httpMethod ${call.request.uri}
                $headers
                Status: $status
            """.trimIndent().trimMargin()
        }
    }*/
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(WebSockets) {
        this.timeoutMillis = 5000
        this.maxFrameSize = 65635
        this.pingPeriodMillis = 10000
    }
    routing {
        webSocketRouting(dbRepository, clientManager)
        restRouting(dbRepository, mediaPath)
    }
}