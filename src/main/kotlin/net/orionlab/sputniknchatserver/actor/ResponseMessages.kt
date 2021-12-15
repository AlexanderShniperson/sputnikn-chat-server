package net.orionlab.sputniknchatserver.actor

import com.google.protobuf.GeneratedMessageV3
import java.util.*
import net.orionlab.sputniknchatmessage.RoomDetail as PRoomDetail
import net.orionlab.sputniknchatmessage.RoomMemberDetail as PRoomMemberDetail
import net.orionlab.sputniknchatmessage.UserDetail as PUserDetail
import net.orionlab.sputniknchatmessage.RoomEventMessageDetail as PRoomEventMessageDetail
import net.orionlab.sputniknchatmessage.RoomEventReactionDetail as PRoomEventReactionDetail
import net.orionlab.sputniknchatmessage.RoomEventSystemDetail as PRoomEventSystemDetail
import net.orionlab.sputniknchatmessage.ChatAttachmentDetail as PChatAttachmentDetail
import net.orionlab.sputniknchatmessage.RoomMemberStatusType as PRoomMemberStatusType

abstract class BaseResponse<T : GeneratedMessageV3> {
    abstract val responseId: Int
    abstract fun toProto(): T
}

data class UserDetail(
    val userId: UUID,
    val fullName: String,
    val avatar: String?,
) {
    fun toProto(): PUserDetail {
        return PUserDetail.newBuilder().apply {
            userId = this@UserDetail.userId.toString()
            fullName = this@UserDetail.fullName
            this@UserDetail.avatar?.let { avatar = it }
        }.build()
    }

    override fun hashCode(): Int {
        return userId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserDetail

        if (userId != other.userId) return false

        return true
    }
}

data class AuthUserResponse(
    override val responseId: Int,
    val detail: UserDetail
) : BaseResponse<net.orionlab.sputniknchatmessage.AuthUserReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.AuthUserReply {
        return net.orionlab.sputniknchatmessage.AuthUserReply.newBuilder().apply {
            detail = this@AuthUserResponse.detail.toProto()
        }.build()
    }
}

@Suppress("MemberVisibilityCanBePrivate")
data class RoomMemberDetail(
    val userId: UUID,
    val fullName: String,
    val isOnline: Boolean,
    val memberStatus: net.orionlab.sputniknchatserver.db.enums.MemberStatus,
    val avatar: String?,
    val lastReadMarker: Long?
) {
    fun toProto(): PRoomMemberDetail {
        return PRoomMemberDetail.newBuilder().apply {
            userId = this@RoomMemberDetail.userId.toString()
            fullName = this@RoomMemberDetail.fullName
            isOnline = this@RoomMemberDetail.isOnline
            memberStatus = memberStatusToProto()
            this@RoomMemberDetail.avatar?.let { avatar = it }
            this@RoomMemberDetail.lastReadMarker?.let { lastReadMarker = it }
        }.build()
    }

    fun memberStatusToProto(): PRoomMemberStatusType {
        return when (this@RoomMemberDetail.memberStatus) {
            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_INVITED -> PRoomMemberStatusType.roomMemberStatusTypeInvited
            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_JOINED -> PRoomMemberStatusType.roomMemberStatusTypeJoined
            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_LEFT -> PRoomMemberStatusType.roomMemberStatusTypeLeft
            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_KICKED -> PRoomMemberStatusType.roomMemberStatusTypeKicked
            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_BANNED -> PRoomMemberStatusType.roomMemberStatusTypeBanned
        }
    }
}

data class RoomDetail(
    val roomId: UUID,
    val title: String,
    val avatar: String?,
    val members: List<RoomMemberDetail>,
    val eventMessageUnreadCount: Int,
    val eventSystemUnreadCount: Int
) {
    fun toProto(): PRoomDetail {
        return PRoomDetail.newBuilder().apply {
            roomId = this@RoomDetail.roomId.toString()
            title = this@RoomDetail.title
            this@RoomDetail.avatar?.let { avatar = it }
            addAllMembers(this@RoomDetail.members.map { it.toProto() })
            eventMessageUnreadCount = this@RoomDetail.eventMessageUnreadCount
            eventSystemUnreadCount = this@RoomDetail.eventSystemUnreadCount
        }.build()
    }
}

data class ListRoomsResponse(
    override val responseId: Int,
    val roomDetail: List<RoomDetail>
) : BaseResponse<net.orionlab.sputniknchatmessage.ListRoomsReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.ListRoomsReply {
        return net.orionlab.sputniknchatmessage.ListRoomsReply.newBuilder().apply {
            addAllDetail(this@ListRoomsResponse.roomDetail.map { it.toProto() })
        }.build()
    }
}

data class ListUsersResponse(
    override val responseId: Int,
    val users: List<UserDetail>
) : BaseResponse<net.orionlab.sputniknchatmessage.ListUsersReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.ListUsersReply {
        return net.orionlab.sputniknchatmessage.ListUsersReply.newBuilder().apply {
            addAllUsers(users.map { it.toProto() })
        }.build()
    }
}

data class ChatAttachmentDetail(
    val eventId: String,
    val attachmentId: String,
    val mimeType: String
) {
    fun toProto(): PChatAttachmentDetail {
        return PChatAttachmentDetail.newBuilder().apply {
            eventId = this@ChatAttachmentDetail.eventId
            attachmentId = this@ChatAttachmentDetail.attachmentId
            mimeType = this@ChatAttachmentDetail.mimeType
        }.build()
    }
}

data class RoomEventMessageDetail(
    val eventId: String,
    val roomId: String,
    val senderId: String,
    val clientEventId: Int?,
    val version: Int,
    val attachment: List<ChatAttachmentDetail>,
    val reaction: List<RoomEventReactionDetail>,
    val content: String,
    val createTimestamp: Long,
    val updateTimestamp: Long
) {
    fun toProto(): PRoomEventMessageDetail {
        return PRoomEventMessageDetail.newBuilder().apply {
            eventId = this@RoomEventMessageDetail.eventId
            roomId = this@RoomEventMessageDetail.roomId
            senderId = this@RoomEventMessageDetail.senderId
            this@RoomEventMessageDetail.clientEventId?.let {
                clientEventId = it
            }
            version = this@RoomEventMessageDetail.version
            addAllAttachment(this@RoomEventMessageDetail.attachment.map { it.toProto() })
            addAllReaction(this@RoomEventMessageDetail.reaction.map { it.toProto() })
            content = this@RoomEventMessageDetail.content
            createTimestamp = this@RoomEventMessageDetail.createTimestamp
            updateTimestamp = this@RoomEventMessageDetail.updateTimestamp
        }.build()
    }
}

data class RoomEventReactionDetail(
    val eventId: String,
    val roomId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long
) {
    fun toProto(): PRoomEventReactionDetail {
        return PRoomEventReactionDetail.newBuilder().apply {
            eventId = this@RoomEventReactionDetail.eventId
            roomId = this@RoomEventReactionDetail.roomId
            senderId = this@RoomEventReactionDetail.senderId
            content = this@RoomEventReactionDetail.content
            timestamp = this@RoomEventReactionDetail.timestamp
        }.build()
    }
}

data class RoomEventReactionResponse(
    override val responseId: Int,
    val detail: RoomEventReactionDetail
) : BaseResponse<net.orionlab.sputniknchatmessage.RoomEventReactionReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.RoomEventReactionReply {
        return net.orionlab.sputniknchatmessage.RoomEventReactionReply.newBuilder().apply {
            detail = this@RoomEventReactionResponse.detail.toProto()
        }.build()
    }
}

data class RoomEventSystemDetail(
    val eventId: String,
    val roomId: String,
    val version: Int,
    val content: String,
    val createTimestamp: Long
) {
    fun toProto(): PRoomEventSystemDetail {
        return PRoomEventSystemDetail.newBuilder().apply {
            eventId = this@RoomEventSystemDetail.eventId
            roomId = this@RoomEventSystemDetail.roomId
            version = this@RoomEventSystemDetail.version
            content = this@RoomEventSystemDetail.content
            createTimestamp = this@RoomEventSystemDetail.createTimestamp
        }.build()
    }
}

data class RoomEventMessageResponse(
    override val responseId: Int,
    val detail: RoomEventMessageDetail
) : BaseResponse<net.orionlab.sputniknchatmessage.RoomEventMessageReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.RoomEventMessageReply {
        return net.orionlab.sputniknchatmessage.RoomEventMessageReply.newBuilder().apply {
            detail = this@RoomEventMessageResponse.detail.toProto()
        }.build()
    }
}

data class SyncRoomsResponse(
    override val responseId: Int,
    val messageEvents: List<RoomEventMessageDetail>,
    val systemEvents: List<RoomEventSystemDetail>
) : BaseResponse<net.orionlab.sputniknchatmessage.SyncRoomsReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.SyncRoomsReply {
        return net.orionlab.sputniknchatmessage.SyncRoomsReply.newBuilder().apply {
            addAllMessageEvents(this@SyncRoomsResponse.messageEvents.map { it.toProto() })
            addAllSystemEvents(this@SyncRoomsResponse.systemEvents.map { it.toProto() })
        }.build()
    }
}

data class CreateRoomResponse(
    override val responseId: Int,
    val detail: RoomDetail
) : BaseResponse<net.orionlab.sputniknchatmessage.CreateRoomReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.CreateRoomReply {
        return net.orionlab.sputniknchatmessage.CreateRoomReply.newBuilder().apply {
            detail = this@CreateRoomResponse.detail.toProto()
        }.build()
    }
}

data class RoomStateChangedResponse(
    override val responseId: Int,
    val detail: RoomDetail
) : BaseResponse<net.orionlab.sputniknchatmessage.RoomStateChangedReply>() {
    override fun toProto(): net.orionlab.sputniknchatmessage.RoomStateChangedReply {
        return net.orionlab.sputniknchatmessage.RoomStateChangedReply.newBuilder().apply {
            detail = this@RoomStateChangedResponse.detail.toProto()
        }.build()
    }
}