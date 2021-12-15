package net.orionlab.sputniknchatserver.actor

import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.Props
import net.orionlab.sputniknchatserver.db.DbRepository
import net.orionlab.sputniknchatserver.util.parseUUIDSafe
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class RoomManagerActor(
    val dbRepository: DbRepository
) : AbstractLoggingActor() {
    companion object {
        fun props(dbRepository: DbRepository): Props {
            return Props.create(RoomManagerActor::class.java, dbRepository)
        }

        fun actorName() = "RoomManager"
    }

    override fun preStart() {
        super.preStart()
        dbRepository.getRoomDao().getRooms().forEach {
            startRoom(it.id, it.title, it.avatar)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClientActor.Companion.CreateRoomRequestInternal::class.java, this::onAddRoomAndRun)
            .matchAny { log().warning("Unhandled message '$it'") }
            .build()
    }

    private fun sendToClientManager(request: Any, senderRef: ActorRef? = null) {
        context.actorSelection("/user/${ClientManagerActor.actorName()}").tell(request, senderRef ?: self)
    }

    fun startRoom(roomId: UUID, title: String, avatar: String?) {
        val actorName = RoomActor.actorName(roomId)
        context.actorOf(RoomActor.props(dbRepository, roomId, title, avatar), actorName)
    }

    fun onAddRoomAndRun(request: ClientActor.Companion.CreateRoomRequestInternal) {
        val uniqueMembers = request.request.memberIds.mapNotNull { it.parseUUIDSafe() }.toSet()
        val foundUsers = dbRepository.getUserDao().findUsers(uniqueMembers)
        val response = if (foundUsers.size < 2) {
            ClientActor.Companion.CreateRoomResponseInternal(
                creatorUserId = request.creatorUserId,
                requestId = request.request.requestId,
                error = net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeRoomRequiredMinMembers,
                result = null as RoomDetail?
            )
        } else {
            val tmpRoom = net.orionlab.sputniknchatserver.db.tables.pojos.Room(
                null,
                request.request.title,
                request.request.avatar,
                null,
                null
            )
            val newRoom = dbRepository.getRoomDao().addRoom(
                tmpRoom,
                request.creatorUserId,
                foundUsers.map { it.id }.toSet()
            )
            if (newRoom == null) {
                ClientActor.Companion.CreateRoomResponseInternal(
                    creatorUserId = request.creatorUserId,
                    requestId = request.request.requestId,
                    error = net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeInternalError,
                    result = null as RoomDetail?
                )
            } else {
                startRoom(newRoom.id, tmpRoom.title, tmpRoom.avatar)
                // TODO: avoid to send same requestId to all recipients
                ClientActor.Companion.CreateRoomResponseInternal(
                    creatorUserId = request.creatorUserId,
                    requestId = request.request.requestId,
                    error = net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone,
                    result = RoomDetail(
                        roomId = newRoom.id,
                        title = newRoom.title,
                        avatar = newRoom.avatar,
                        members = foundUsers.map {
                            RoomMemberDetail(
                                userId = it.id,
                                fullName = it.fullName,
                                isOnline = false,
                                memberStatus = net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_INVITED,
                                avatar = it.avatar,
                                lastReadMarker = null
                            )
                        },
                        eventMessageUnreadCount = 0,
                        eventSystemUnreadCount = 0
                    )
                )
            }
        }
        // if no success send error to author of creation, otherwise send response to all possible members
        if (response.result == null) {
            context.sender.tell(response, self)
        } else {
            val roomActorName = RoomActor.actorName(response.result.roomId)
            context.child(roomActorName).foreach { roomRef ->
                sendToClientManager(
                    ClientActor.Companion.MessageToSpecificUser(
                        response.result.members.map { it.userId },
                        response
                    ),
                    roomRef
                )
            }
        }
    }


}