package net.orionlab.sputniknchatserver.actor

import akka.actor.AbstractLoggingActor
import akka.actor.Props
import akka.actor.ReceiveTimeout
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

typealias InnerMsgType = Any

@Suppress("MemberVisibilityCanBePrivate")
class MessageCollectorActor<InnerMsgType>(
    val messageCount: Int,
    val completeCallback: (List<InnerMsgType>) -> Unit
) : AbstractLoggingActor() {
    companion object {
        fun <InnerMsgType> props(
            messageCount: Int,
            messageBuilder: (List<InnerMsgType>) -> Unit
        ): Props {
            return Props.create(
                MessageCollectorActor::class.java,
                messageCount,
                messageBuilder
            )
        }
    }

    private val collector = mutableListOf<InnerMsgType>()

    override fun preStart() {
        super.preStart()
        context.setReceiveTimeout(Duration.create(3000, TimeUnit.MILLISECONDS))
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ReceiveTimeout::class.java) {
                context.stop(self)
            }
            .matchAny {
                (it as? InnerMsgType)?.let { innerMsg ->
                    collector.add(innerMsg)
                    if (collector.size == messageCount) {
                        context.cancelReceiveTimeout()
                        completeCallback(collector)
                        context.stop(self)
                    }
                }
            }.build()
    }
}