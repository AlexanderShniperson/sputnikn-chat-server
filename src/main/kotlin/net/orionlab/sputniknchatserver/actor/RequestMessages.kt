package net.orionlab.sputniknchatserver.actor

import net.orionlab.sputniknchatserver.util.parseUUIDSafe
import java.util.*
import net.orionlab.sputniknchatmessage.RemoveRoomMember as PRemoveRoomMember
import net.orionlab.sputniknchatmessage.InviteRoomMember as PInviteRoomMember
import net.orionlab.sputniknchatmessage.SinceTimeOrderType as PSinceTimeOrderType
import net.orionlab.sputniknchatmessage.RoomEventType as PRoomEventType
import net.orionlab.sputniknchatmessage.AuthUser as PAuthUser
import net.orionlab.sputniknchatmessage.ListRooms as PListRooms
import net.orionlab.sputniknchatmessage.ListUsers as PListUsers
import net.orionlab.sputniknchatmessage.RoomEventMessage as PRoomEventMessage
import net.orionlab.sputniknchatmessage.RoomEventReaction as PRoomEventReaction
import net.orionlab.sputniknchatmessage.SetRoomReadMarker as PSetRoomReadMarker
import net.orionlab.sputniknchatmessage.SyncRoomFilter as PSyncRoomFilter
import net.orionlab.sputniknchatmessage.SyncRooms as PSyncRooms
import net.orionlab.sputniknchatmessage.SinceTimeFilter as PSinceTimeFilter
import net.orionlab.sputniknchatmessage.CreateRoom as PCreateRoom

abstract class BaseRequest(open val requestId: Int)

data class AuthUserRequest(
    override val requestId: Int,
    val login: String,
    val password: String
) : BaseRequest(requestId) {
    companion object {
        @JvmStatic
        fun fromProto(requestId: Int, proto: PAuthUser): AuthUserRequest {
            return AuthUserRequest(
                requestId,
                proto.login,
                proto.password
            )
        }
    }
}

data class ListRoomsRequest(
    override val requestId: Int,
    val roomIds: List<UUID>
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PListRooms): ListRoomsRequest {
            return ListRoomsRequest(
                requestId,
                proto.roomIdsList.mapNotNull { it.parseUUIDSafe() }
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
data class ListUsersRequest(
    override val requestId: Int,
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PListUsers): ListUsersRequest {
            return ListUsersRequest(requestId)
        }
    }
}

data class RoomEventMessageRequest(
    override val requestId: Int,
    val roomId: String,
    val clientEventId: Int,
    val attachment: List<UUID>,
    val content: String,
    val version: Int
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PRoomEventMessage): RoomEventMessageRequest {
            return RoomEventMessageRequest(
                requestId,
                proto.roomId,
                proto.clientEventId,
                proto.attachmentList.mapNotNull { it.parseUUIDSafe() },
                proto.content,
                proto.version
            )
        }
    }
}

data class RoomEventReactionRequest(
    override val requestId: Int,
    val roomId: String,
    val messageId: String,
    val clientTransactionId: Int,
    val content: String
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PRoomEventReaction): RoomEventReactionRequest {
            return RoomEventReactionRequest(
                requestId,
                proto.roomId,
                proto.messageId,
                proto.clientEventId,
                proto.content
            )
        }
    }
}

enum class SinceTimeOrderType {
    SinceTimeOrderTypeNewest,
    SinceTimeOrderTypeOldest;

    companion object {
        fun fromProto(proto: PSinceTimeOrderType): SinceTimeOrderType {
            return when (proto) {
                PSinceTimeOrderType.sinceTimeOrderTypeNewest -> SinceTimeOrderTypeNewest
                PSinceTimeOrderType.sinceTimeOrderTypeOldest -> SinceTimeOrderTypeOldest
            }
        }
    }
}

data class SinceTimeFilter(
    val sinceTime: Long,
    val orderType: SinceTimeOrderType
) {
    companion object {
        fun fromProto(proto: PSinceTimeFilter): SinceTimeFilter {
            return SinceTimeFilter(
                proto.sinceTimestamp,
                SinceTimeOrderType.fromProto(proto.orderType)
            )
        }
    }
}

enum class RoomEventType {
    RoomEventTypeAll,
    RoomEventTypeMessage,
    RoomEventTypeSystem;

    companion object {
        fun fromProto(proto: PRoomEventType): RoomEventType {
            return when (proto) {
                PRoomEventType.roomEventTypeAll -> RoomEventTypeAll
                PRoomEventType.roomEventTypeMessage -> RoomEventTypeMessage
                PRoomEventType.roomEventTypeSystem -> RoomEventTypeSystem
            }
        }
    }
}

data class SyncRoomFilter(
    val roomId: String,
    val sinceTimeFilter: SinceTimeFilter?,
    val eventFilter: RoomEventType,
    val eventLimit: Int
) {
    companion object {
        fun fromProto(proto: PSyncRoomFilter): SyncRoomFilter {
            return SyncRoomFilter(
                proto.roomId,
                if (proto.hasSinceFilter()) SinceTimeFilter.fromProto(proto.sinceFilter) else null,
                RoomEventType.fromProto(proto.eventFilter),
                proto.eventLimit
            )
        }
    }
}

data class SyncRoomsRequest(
    override val requestId: Int,
    val roomFilter: List<SyncRoomFilter>
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PSyncRooms): SyncRoomsRequest {
            return SyncRoomsRequest(
                requestId,
                proto.roomFilterList.toList().map { SyncRoomFilter.fromProto(it) }
            )
        }
    }
}

data class SetRoomReadMarkerRequest(
    override val requestId: Int,
    val roomId: String,
    val readMarkerTimestamp: Long
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PSetRoomReadMarker): SetRoomReadMarkerRequest {
            return SetRoomReadMarkerRequest(
                requestId,
                proto.roomId,
                proto.readMarkerTimestamp
            )
        }
    }
}

data class CreateRoomRequest(
    override val requestId: Int,
    val title: String,
    val avatar: String?,
    val memberIds: List<String>
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PCreateRoom): CreateRoomRequest {
            return CreateRoomRequest(
                requestId,
                proto.title,
                if (proto.hasAvatar()) proto.avatar else null,
                proto.memberIdsList.toList()
            )
        }
    }
}

data class InviteRoomMemberRequest(
    override val requestId: Int,
    val roomId: String,
    val memberIds: List<String>
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PInviteRoomMember): InviteRoomMemberRequest {
            return InviteRoomMemberRequest(
                requestId,
                proto.roomId,
                proto.memberIdsList.toList()
            )
        }
    }
}

data class RemoveRoomMemberRequest(
    override val requestId: Int,
    val roomId: String,
    val memberIds: List<String>
) : BaseRequest(requestId) {
    companion object {
        fun fromProto(requestId: Int, proto: PRemoveRoomMember): RemoveRoomMemberRequest {
            return RemoveRoomMemberRequest(
                requestId,
                proto.roomId,
                proto.memberIdsList.toList()
            )
        }
    }
}