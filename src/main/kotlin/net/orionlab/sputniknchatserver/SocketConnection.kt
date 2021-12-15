package net.orionlab.sputniknchatserver

import akka.actor.ActorRef
import net.orionlab.sputniknchatmessage.*
import net.orionlab.sputniknchatmessage.RemoveRoomMember as PRemoveRoomMember
import net.orionlab.sputniknchatmessage.InviteRoomMember as PInviteRoomMember
import net.orionlab.sputniknchatmessage.CreateRoom as PCreateRoom
import net.orionlab.sputniknchatmessage.RoomEventReaction as PRoomEventReaction
import net.orionlab.sputniknchatmessage.RoomEventMessage as PRoomEventMessage
import net.orionlab.sputniknchatmessage.ListUsers as PListUsers
import net.orionlab.sputniknchatmessage.ListRooms as PListRooms
import net.orionlab.sputniknchatmessage.AuthUser as PAuthUser
import net.orionlab.sputniknchatmessage.SetRoomReadMarker as PSetRoomReadMarker
import net.orionlab.sputniknchatmessage.SyncRooms as PSyncRooms
import net.orionlab.sputniknchatserver.actor.*
import com.google.protobuf.GeneratedMessageV3
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.*

@Suppress("MemberVisibilityCanBePrivate")
class SocketConnection(
    val session: DefaultWebSocketSession
) {
    companion object {
        var lastId = AtomicInteger(0)
    }

    val name = "SocketConnection${lastId.getAndIncrement()}"
    private val _serializer = net.orionlab.sputniknchatmessage.Serializer()
    private val _log = LoggerFactory.getLogger(name)

    private var _chatClient: ActorRef? = null

    fun setChatClient(ref: ActorRef) {
        _chatClient = ref
    }

    fun processRequest(frame: Frame) {
        try {
            if (frame is Frame.Binary) {
                val transport = TransportRequest.parseFrom(frame.data)
                val protoMsg = _serializer.bytesToMessage(transport.msgId, transport.data.toByteArray())
                protoMsg?.let {
                    val result = when (it) {
                        is PAuthUser ->
                            AuthUserRequest.fromProto(transport.requestId, it)
                        is PListRooms ->
                            ListRoomsRequest.fromProto(transport.requestId, it)
                        is PListUsers ->
                            ListUsersRequest.fromProto(transport.requestId, it)
                        is PRoomEventMessage ->
                            RoomEventMessageRequest.fromProto(transport.requestId, it)
                        is PRoomEventReaction ->
                            RoomEventReactionRequest.fromProto(transport.requestId, it)
                        is PSyncRooms ->
                            SyncRoomsRequest.fromProto(transport.requestId, it)
                        is PSetRoomReadMarker ->
                            SetRoomReadMarkerRequest.fromProto(transport.requestId, it)
                        is PCreateRoom ->
                            CreateRoomRequest.fromProto(transport.requestId, it)
                        is PInviteRoomMember ->
                            InviteRoomMemberRequest.fromProto(transport.requestId, it)
                        is PRemoveRoomMember ->
                            RemoveRoomMemberRequest.fromProto(transport.requestId, it)
                        else -> {
                            _log.warn("Unhandled message type '$it'")
                            null
                        }
                    }
                    result?.let { res ->
                        _log.info("Receive $res")
                        _chatClient?.tell(res, ActorRef.noSender())
                    }
                }

            }
        } catch (ex: Exception) {
            _log.error(ex)
        }
    }

    fun <A : GeneratedMessageV3> processResponse(
        responseId: Int,
        response: BaseResponse<A>?,
        errorType: ResponseErrorType
    ) {
        try {
            val protoMsg = response?.toProto()
            val protoMsgId = protoMsg?.let { _serializer.messageToId(it) } ?: 0
            if (protoMsgId == 0 && errorType == ResponseErrorType.responseErrorTypeNone) return
            val tmpResponseId = response?.responseId ?: responseId
            val result = TransportResponse.newBuilder().apply {
                this.responseId = tmpResponseId
                msgId = protoMsgId
                this.errorType = errorType
                protoMsg?.toByteString()?.let { data = it }
            }.build()
            CoroutineScope(session.coroutineContext).launch {
                val bytes = result.toByteArray()
                session.send(bytes)
            }
        } catch (ex: Exception) {
            _log.error(ex)
        }
    }

    fun close() {
        _chatClient?.tell(ClientActor.Companion.StopClient(), ActorRef.noSender())
    }
}