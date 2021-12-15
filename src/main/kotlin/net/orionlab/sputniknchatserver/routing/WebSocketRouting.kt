package net.orionlab.sputniknchatserver.routing

import akka.actor.ActorRef
import net.orionlab.sputniknchatserver.SocketConnection
import net.orionlab.sputniknchatserver.actor.ClientActor
import net.orionlab.sputniknchatserver.db.DbRepository
import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.websocket.*
import java.util.*

fun Route.webSocketRouting(dbRepository: DbRepository, clientManager: ActorRef) {
    val connections = Collections.synchronizedSet<SocketConnection>(LinkedHashSet())
    val log = application.log
    webSocket("/chat") {
        log.debug("New socket client!")
        val thisConnection = SocketConnection(this)
        clientManager.tell(ClientActor.props(dbRepository, thisConnection), ActorRef.noSender())
        connections += thisConnection
        try {
            for (frame in incoming) {
                thisConnection.processRequest(frame)
            }
        } catch (ex: Throwable) {
            log.error("", ex)
        } finally {
            thisConnection.close()
            log.debug("Removing $thisConnection!")
            connections -= thisConnection
        }
    }
}