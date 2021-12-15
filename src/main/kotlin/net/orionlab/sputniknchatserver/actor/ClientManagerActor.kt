package net.orionlab.sputniknchatserver.actor

import akka.actor.AbstractLoggingActor
import akka.actor.Props

class ClientManagerActor : AbstractLoggingActor() {
    companion object {
        fun props(): Props {
            return Props.create(ClientManagerActor::class.java)
        }

        fun actorName() = "ClientManager"
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Props::class.java) {
                context.actorOf(it)
            }
            .match(ClientActor.Companion.MessageToSpecificUser::class.java) { msg ->
                context.children.forEach {
                    it.forward(msg, context)
                }
            }
            .matchAny { log().warning("Unhandled message '$it'") }
            .build()
    }
}