package net.orionlab.sputniknchatserver.actor

import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import net.orionlab.sputniknchatserver.db.DbRepository
import net.orionlab.sputniknchatserver.db.entity.RoomMemberEntity
import net.orionlab.sputniknchatserver.db.entity.RoomMemberUnreadEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class RoomActor(
    private val dbRepository: DbRepository,
    private val roomId: UUID,
    title: String,
    avatar: String?
) : AbstractLoggingActor() {
    companion object {
        const val maxSincEvents = 100
        const val defaultSincEvents = 30
        val minSincDate: LocalDateTime = LocalDateTime.of(2021, 1, 1, 0, 0)

        fun actorName(roomId: UUID): String {
            return "Room-$roomId"
        }

        fun props(dbRepository: DbRepository, roomId: UUID, title: String, avatar: String?): Props {
            return Props.create(RoomActor::class.java, dbRepository, roomId, title, avatar)
        }

        data class JoinChatClient(val clientRef: ActorRef, val userId: UUID)
        data class ListRoomsRequestInternal(val clientRef: ActorRef, val data: ListRoomsRequest)
        data class SyncRoomsRequestInternal(val userId: UUID, val data: SyncRoomsRequest)
    }

    private val possibleMembers = mutableSetOf<RoomMemberEntity>()
    private val onlineChatMembers = mutableListOf<JoinChatClient>()
    private val memberUnreads = mutableSetOf<RoomMemberUnreadEntity>()
    private var _title: String = title
    private var _avatar: String? = avatar

    override fun preStart() {
        super.preStart()
        possibleMembers.clear()
        possibleMembers.addAll(dbRepository.getRoomDao().getRoomMembers(roomId))
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(JoinChatClient::class.java, this::onJoinChatClient)
            .match(ListRoomsRequestInternal::class.java, this::onListRoomRequest)
            .match(SyncRoomsRequestInternal::class.java, this::onSyncRoomsRequest)
            .match(SetRoomReadMarkerRequest::class.java, this::onSetRoomReadMarkerRequest)
            .match(RoomEventMessageRequest::class.java, this::onRoomEventMessageRequest)
            .match(InviteRoomMemberRequest::class.java, this::onInviteRoomMemberRequest)
            .match(RemoveRoomMemberRequest::class.java, this::onRemoveRoomMemberRequest)
            .match(Terminated::class.java, this::onTerminated)
            .matchAny { log().warning("Unhandled message '$it'") }
            .build()
    }

    fun sendToClientManager(msg: Any) {
        context.actorSelection("/user/${ClientManagerActor.actorName()}").tell(msg, self)
    }

    fun onJoinChatClient(msg: JoinChatClient) {
        if (possibleMembers.any { it.userId == msg.userId }) {
            onlineChatMembers.add(msg)
            context.watch(msg.clientRef)
            onlineChatMembers.forEach { member ->
                val response = buildRoomStateResponse(member.userId)
                member.clientRef.tell(response, self)
            }
        }
    }

    fun onListRoomRequest(msg: ListRoomsRequestInternal) {
        val foundMember = onlineChatMembers.find { it.clientRef == msg.clientRef }
        foundMember?.let { existMember ->
            if (msg.data.roomIds.contains(roomId)) {
                val response = buildRoomState(existMember.userId)
                context.sender.tell(response, self)
            }
        }
    }

    fun onSyncRoomsRequest(msg: SyncRoomsRequestInternal) {
        val foundRoom = msg.data.roomFilter.find { it.roomId == roomId.toString() }
        if (msg.data.roomFilter.isEmpty() || foundRoom != null) {
            val eventFilter =
                foundRoom?.eventFilter ?: RoomEventType.RoomEventTypeAll
            val eventLimit =
                foundRoom?.eventLimit
                    ?.let { if (it > maxSincEvents) maxSincEvents else it }
                    ?: defaultSincEvents
            val sinceTimeFilter = foundRoom?.sinceTimeFilter?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.sinceTime), ZoneOffset.UTC)
            } ?: minSincDate
            val sinceTimeOrder = foundRoom?.sinceTimeFilter?.orderType
                ?: SinceTimeOrderType.SinceTimeOrderTypeNewest

            val roomEvents = dbRepository.getRoomDao().getRoomEvents(
                roomId,
                eventFilter,
                eventLimit,
                sinceTimeFilter,
                sinceTimeOrder
            )
            val response = SyncRoomsResponse(
                responseId = msg.data.requestId,
                messageEvents = roomEvents.messageEvents.map { event ->
                    RoomEventMessageDetail(
                        eventId = event.id.toString(),
                        roomId = roomId.toString(),
                        senderId = event.userId.toString(),
                        if (event.userId == msg.userId) event.clientEventId else null,
                        version = event.version.toInt(),
                        attachment = roomEvents.attachmentEvents
                            .filter { it.eventId == event.id.toString() }
                            .map { it.detail },
                        reaction = roomEvents.reactionEvents
                            .filter { it.eventId == event.id.toString() }
                            .map { it.detail },
                        content = event.content,
                        createTimestamp = event.dateCreate.toInstant(ZoneOffset.UTC).toEpochMilli(),
                        updateTimestamp = (event.dateEdit ?: event.dateCreate).toInstant(ZoneOffset.UTC).toEpochMilli()
                    )
                },
                systemEvents = roomEvents.systemEvents.map {
                    RoomEventSystemDetail(
                        eventId = it.id.toString(),
                        roomId = roomId.toString(),
                        version = it.version.toInt(),
                        content = it.content,
                        createTimestamp = it.dateCreate.toInstant(ZoneOffset.UTC).toEpochMilli()
                    )
                }
            )
            context.sender.tell(response, self)
        }
    }

    fun onSetRoomReadMarkerRequest(msg: SetRoomReadMarkerRequest) {
        val foundMember = onlineChatMembers.find { it.clientRef == sender }
        foundMember?.let { existMember ->
            val result = dbRepository.getRoomDao().setMemberReadMarker(
                roomId,
                existMember.userId,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.readMarkerTimestamp), ZoneOffset.UTC)
            )
            if (result) {
                possibleMembers.find { it.userId == existMember.userId }?.let {
                    it.lastReadMarker = msg.readMarkerTimestamp
                }
                onlineChatMembers.forEach { member ->
                    val response = buildRoomStateResponse(member.userId)
                    member.clientRef.tell(response, self)
                }
            }
        }
    }

    fun onRoomEventMessageRequest(msg: RoomEventMessageRequest) {
        val foundMember = onlineChatMembers.find { it.clientRef == sender }
        foundMember?.let { member ->
            dbRepository.getRoomDao().addRoomEventMessage(
                net.orionlab.sputniknchatserver.db.tables.pojos.RoomEventMessage(
                    null,
                    UUID.fromString(msg.roomId),
                    member.userId,
                    msg.clientEventId,
                    msg.version.toShort(),
                    msg.content,
                    null,
                    null,
                ),
                msg.attachment
            )?.let { event ->
                onlineChatMembers.forEach {
                    // TODO(alexsh): avoid to send same requestId to all recipients
                    val tmpResponseId = if (member.clientRef == it.clientRef) msg.requestId else -1
                    val response = RoomEventMessageResponse(
                        responseId = tmpResponseId,
                        detail = RoomEventMessageDetail(
                            eventId = event.event.id.toString(),
                            roomId = event.event.roomId.toString(),
                            senderId = member.userId.toString(),
                            clientEventId = if (member.userId == event.event.userId) msg.clientEventId else null,
                            version = msg.version,
                            attachment = event.attachments,
                            reaction = emptyList(),
                            content = event.event.content,
                            createTimestamp = event.event.dateCreate.toInstant(ZoneOffset.UTC).toEpochMilli(),
                            updateTimestamp = (event.event.dateEdit ?: event.event.dateCreate).toInstant(ZoneOffset.UTC)
                                .toEpochMilli()
                        )
                    )
                    it.clientRef.tell(response, self)
                }
            }
        }
    }

    fun buildRoomState(destMemberId: UUID): RoomDetail {
        val destMemberUnreads = memberUnreads.find { it.memberId == destMemberId }
        return RoomDetail(
            roomId = roomId,
            title = _title,
            avatar = _avatar,
            members = possibleMembers.map { member ->
                RoomMemberDetail(
                    userId = member.userId,
                    fullName = member.fullName,
                    isOnline = onlineChatMembers.any { it.userId == member.userId },
                    memberStatus = member.memberStatus,
                    avatar = member.avatar,
                    lastReadMarker = member.lastReadMarker
                )
            },
            eventMessageUnreadCount = destMemberUnreads?.eventMessageUnread ?: 0,
            eventSystemUnreadCount = destMemberUnreads?.eventSystemUnread ?: 0
        )
    }

    fun buildRoomStateResponse(destMemberId: UUID): RoomStateChangedResponse {
        calculateUserUnreads()
        return RoomStateChangedResponse(
            responseId = -1,
            detail = buildRoomState(destMemberId)
        )
    }

    fun calculateUserUnreads() {
        memberUnreads.clear()
        memberUnreads.addAll(
            dbRepository.getRoomDao().getMemberUnreads(roomId)
        )
    }

    fun onInviteRoomMemberRequest(msg: InviteRoomMemberRequest) {
        val absentUsers = possibleMembers.filter { !msg.memberIds.contains(it.userId.toString()) }
            .map { it.userId }
        val addedUsers = dbRepository.getRoomDao().addRoomMembers(
            roomId,
            absentUsers
        )
        possibleMembers.addAll(addedUsers)
        onlineChatMembers.forEach { member ->
            val response = buildRoomStateResponse(member.userId)
            member.clientRef.tell(response, self)
        }
        // new members is still not connected let's find them by ClientManager and send Response
        addedUsers.forEach { addedUser ->
            val response = buildRoomStateResponse(addedUser.userId)
            sendToClientManager(
                ClientActor.Companion.MessageToSpecificUser(
                    userIds = listOf(addedUser.userId),
                    message = response
                )
            )
        }
    }

    fun onRemoveRoomMemberRequest(msg: RemoveRoomMemberRequest) {
        // TODO: what should we do at DB?
        val membersToRemove = possibleMembers.filter { msg.memberIds.contains(it.userId.toString()) }
        dbRepository.getRoomDao().setRoomMemberStatus(
            roomId,
            membersToRemove.associate { it.userId to net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_LEFT }
        )
        onlineChatMembers.forEach { member ->
            val response = buildRoomStateResponse(member.userId)
            member.clientRef.tell(response, self)
        }
        possibleMembers.removeAll { member -> membersToRemove.any { it.userId == member.userId } }
        onlineChatMembers.filter { member -> membersToRemove.any { it.userId == member.userId } }.forEach {
            context.unwatch(it.clientRef)
            onlineChatMembers.remove(it)
        }
    }

    fun onTerminated(msg: Terminated) {
        onlineChatMembers.find { it.clientRef == msg.actor }?.let { leaveClient ->
            onlineChatMembers.removeAll { it.clientRef == leaveClient.clientRef }
            onlineChatMembers.forEach { member ->
                val response = buildRoomStateResponse(member.userId)
                member.clientRef.tell(response, self)
            }
        }
    }
}