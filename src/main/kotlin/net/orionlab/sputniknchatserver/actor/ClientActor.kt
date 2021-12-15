package net.orionlab.sputniknchatserver.actor

import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.Props
import net.orionlab.sputniknchatserver.SocketConnection
import net.orionlab.sputniknchatserver.db.DbRepository
import java.util.*

@Suppress("MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
class ClientActor(
    val dbRepository: DbRepository,
    val socketConnection: SocketConnection
) : AbstractLoggingActor() {
    companion object {
        fun props(dbRepository: DbRepository, socketConnection: SocketConnection): Props {
            return Props.create(ClientActor::class.java, dbRepository, socketConnection)
        }

        data class StopClient(val dummy: String = "")
        data class MessageToSpecificUser(val userIds: List<UUID>, val message: Any)
        data class CreateRoomRequestInternal(val creatorUserId: UUID, val request: CreateRoomRequest)
        data class CreateRoomResponseInternal(
            val creatorUserId: UUID,
            val requestId: Int,
            val error: net.orionlab.sputniknchatmessage.ResponseErrorType,
            val result: RoomDetail?
        )
    }

    private var _fullName = ""
    private var _avatar: String? = null
    private val _userRoomIds = mutableSetOf<UUID>()

    override fun preStart() {
        super.preStart()
        socketConnection.setChatClient(self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(AuthUserRequest::class.java, this::onAuthRequest)
            .match(StopClient::class.java) {
                context.stop(self)
            }
            .match(BaseResponse::class.java) {}
            .matchAny {
                if (it is BaseRequest) {
                    onUnauthorized(it)
                } else {
                    log().warning("Unhandled message at Unauthorized state '$it'")
                }
            }
            .build()
    }

    fun authorizedReceive(userId: UUID): Receive {
        return receiveBuilder()
            .match(CreateRoomRequest::class.java) {
                onCreateRoomRequest(it, userId)
            }
            .match(ListRoomsRequest::class.java) {
                onListRoomsRequest(it, userId)
            }
            .match(ListUsersRequest::class.java) {
                onListUsersRequest(it, userId)
            }
            .match(RoomEventMessageRequest::class.java) {
                onRoomEventMessageRequest(it, userId)
            }
            .match(RoomEventReactionRequest::class.java) {
                onRoomEventReactionRequest(it, userId)
            }
            .match(SyncRoomsRequest::class.java) {
                onSyncRoomsRequest(it, userId)
            }
            .match(SetRoomReadMarkerRequest::class.java) {
                onSetRoomReadMarkerRequest(it, userId)
            }
            .match(InviteRoomMemberRequest::class.java) {
                onInviteRoomMemberRequest(it, userId)
            }
            .match(RemoveRoomMemberRequest::class.java) {
                onRemoveRoomMemberRequest(it, userId)
            }
            .match(CreateRoomResponseInternal::class.java) {
                onCreateRoomResponseInternal(it, userId)
            }
            .match(BaseResponse::class.java) {
                onReceiveResponse(it, userId)
            }
            .match(MessageToSpecificUser::class.java) {
                if (it.userIds.contains(userId)) {
                    onMessageToSpecificUser(it, userId)
                }
            }
            .match(StopClient::class.java) {
                context.stop(self)
            }
            .matchAny { log().warning("Unhandled message at Authorized state '$it'") }
            .build()
    }

    private fun sendToRoomManager(request: Any) {
        context.actorSelection("/user/${RoomManagerActor.actorName()}").tell(request, self)
    }

    private fun sendToRoom(roomId: UUID, request: Any, replyTo: ActorRef? = null) {
        context.actorSelection("/user/${RoomManagerActor.actorName()}/${RoomActor.actorName(roomId)}")
            .tell(request, replyTo ?: self)
    }

    fun onUnauthorized(msg: BaseRequest) {
        socketConnection.processResponse(
            msg.requestId,
            null as BaseResponse<*>?,
            net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeUserNeedAuth
        )
    }

    fun onCreateRoomRequest(msg: CreateRoomRequest, userId: UUID) {
        sendToRoomManager(CreateRoomRequestInternal(userId, msg))
    }

    fun onAuthRequest(msg: AuthUserRequest) {
        val user = dbRepository.getUserDao().findUserByLoginPassword(msg.login, msg.password)
        val result = user?.let {
            val userId = user.id
            _fullName = user.fullName
            _avatar = user.avatar
            val rooms = dbRepository.getUserDao().findUserRooms(user.id)
            _userRoomIds.clear()
            _userRoomIds.addAll(rooms.map { it.id })
            // NOTE: Change context to Authorized
            context.become(authorizedReceive(userId))
            _userRoomIds.forEach { roomId ->
                sendToRoom(roomId, RoomActor.Companion.JoinChatClient(self, userId))
            }
            AuthUserResponse(
                msg.requestId,
                UserDetail(userId, user.fullName, user.avatar)
            )
        }
        val error = if (result == null) net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeUserNotFound
        else net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone
        socketConnection.processResponse(msg.requestId, result, error)
    }

    fun onListRoomsRequest(msg: ListRoomsRequest, userId: UUID) {
        val possibleRooms = if (msg.roomIds.isEmpty()) {
            _userRoomIds
        } else {
            _userRoomIds.filter { it in msg.roomIds }
        }
        if (possibleRooms.isNotEmpty()) {
            val requestId = msg.requestId
            val messageCollector =
                context.actorOf(MessageCollectorActor.props<RoomDetail>(possibleRooms.size) { data ->
                    self.tell(
                        ListRoomsResponse(requestId, data),
                        ActorRef.noSender()
                    )
                })
            possibleRooms.forEach { roomId ->
                // rebuild message as original can have empty list of rooms
                sendToRoom(
                    roomId,
                    RoomActor.Companion.ListRoomsRequestInternal(
                        clientRef = self,
                        data = msg.copy(roomIds = listOf(roomId))
                    ),
                    messageCollector
                )
            }
        } else {
            onReceiveResponse(ListRoomsResponse(msg.requestId, emptyList()), userId)
        }
    }

    fun onListUsersRequest(msg: ListUsersRequest, userId: UUID) {
        val users = dbRepository.getUserDao().getAllUsers().map {
            UserDetail(
                userId = it.id,
                fullName = it.fullName,
                avatar = it.avatar
            )
        }
        val response = ListUsersResponse(msg.requestId, users)
        socketConnection.processResponse(msg.requestId, response, net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone)
    }

    fun onRoomEventMessageRequest(msg: RoomEventMessageRequest, userId: UUID) {
        _userRoomIds.find { it.toString() == msg.roomId }?.let {
            sendToRoom(it, msg)
        }
    }

    fun onRoomEventReactionRequest(msg: RoomEventReactionRequest, userId: UUID) {
        _userRoomIds.find { it.toString() == msg.roomId }?.let {
            sendToRoom(it, msg)
        }
    }

    fun onSyncRoomsRequest(msg: SyncRoomsRequest, userId: UUID) {
        val possibleRooms = if (msg.roomFilter.isEmpty()) {
            _userRoomIds
        } else {
            _userRoomIds.filter { roomId -> msg.roomFilter.any { it.roomId == roomId.toString() } }
        }
        if (possibleRooms.isEmpty()) {
            socketConnection.processResponse(
                msg.requestId,
                SyncRoomsResponse(
                    responseId = msg.requestId,
                    messageEvents = emptyList(),
                    systemEvents = emptyList()
                ),
                net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone
            )
        } else {
            val requestId = msg.requestId
            val messageCollector =
                context.actorOf(MessageCollectorActor.props<SyncRoomsResponse>(possibleRooms.size) { data ->
                    val messageEvents = data.flatMap { it.messageEvents }
                    val systemEvents = data.flatMap { it.systemEvents }
                    self.tell(
                        SyncRoomsResponse(
                            responseId = requestId,
                            messageEvents = messageEvents,
                            systemEvents = systemEvents
                        ),
                        ActorRef.noSender()
                    )
                })
            possibleRooms.forEach { roomId ->
                sendToRoom(
                    roomId,
                    RoomActor.Companion.SyncRoomsRequestInternal(
                        userId = userId,
                        data = msg
                    ),
                    messageCollector
                )
            }
        }
    }

    fun onSetRoomReadMarkerRequest(msg: SetRoomReadMarkerRequest, userId: UUID) {
        _userRoomIds.find { it.toString() == msg.roomId }?.let {
            sendToRoom(it, msg)
        }
    }

    fun onInviteRoomMemberRequest(msg: InviteRoomMemberRequest, userId: UUID) {
        _userRoomIds.find { it.toString() == msg.roomId }?.let {
            sendToRoom(it, msg)
        }
    }

    fun onRemoveRoomMemberRequest(msg: RemoveRoomMemberRequest, userId: UUID) {
        _userRoomIds.find { it.toString() == msg.roomId }?.let {
            sendToRoom(it, msg)
        }
    }

    fun onMessageToSpecificUser(msg: MessageToSpecificUser, userId: UUID) {
        when (val message = msg.message) {
            is BaseResponse<*> -> {
                onReceiveResponse(message, userId)
            }
            is CreateRoomResponseInternal -> {
                onCreateRoomResponseInternal(message, userId)
            }
            else -> {
                log().warning("Unhandled message at onMessageToSpecificUser '$msg'")
            }
        }
    }

    fun onCreateRoomResponseInternal(msg: CreateRoomResponseInternal, userId: UUID) {
        // this is broadcast message to all members of room or for room creator in case of error
        // check we are in recipients then add room
        msg.result?.let { room ->
            if (room.members.any { it.userId == userId }) {
                _userRoomIds.add(msg.result.roomId)
                sender.tell(RoomActor.Companion.JoinChatClient(self, userId), self)
            }
        }
        // TODO: don't send same responseId to other recipients
        val tmpResponseId = if (userId == msg.creatorUserId) msg.requestId else -1
        socketConnection.processResponse(
            tmpResponseId,
            msg.result?.let {
                CreateRoomResponse(tmpResponseId, it)
            },
            msg.error
        )
    }

    fun onReceiveResponse(msg: BaseResponse<*>, userId: UUID) {
        when (msg) {
            is RoomStateChangedResponse -> {
                val hasRoom = _userRoomIds.contains(msg.detail.roomId)
                val hasMember = msg.detail.members.any { it.userId == userId }
                if (!hasRoom && hasMember) {
                    // Room found us as invited let's join Room
                    sendToRoom(msg.detail.roomId, RoomActor.Companion.JoinChatClient(self, userId))
                    _userRoomIds.add(msg.detail.roomId)
                }
                if (hasRoom && !hasMember) {
                    _userRoomIds.remove(msg.detail.roomId)
                }
                socketConnection.processResponse(msg.responseId, msg, net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone)
            }
            else -> {
                socketConnection.processResponse(msg.responseId, msg, net.orionlab.sputniknchatmessage.ResponseErrorType.responseErrorTypeNone)
            }
        }
    }
}