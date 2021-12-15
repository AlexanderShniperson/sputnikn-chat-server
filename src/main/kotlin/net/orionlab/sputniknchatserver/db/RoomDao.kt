package net.orionlab.sputniknchatserver.db

import net.orionlab.sputniknchatserver.actor.ChatAttachmentDetail
import net.orionlab.sputniknchatserver.actor.RoomEventReactionDetail
import net.orionlab.sputniknchatserver.actor.RoomEventType
import net.orionlab.sputniknchatserver.actor.SinceTimeOrderType
import net.orionlab.sputniknchatserver.db.entity.*
import net.orionlab.sputniknchatserver.db.tables.User as TUser
import net.orionlab.sputniknchatserver.db.tables.Room as TRoom
import net.orionlab.sputniknchatserver.db.tables.RoomMember as TRoomMember
import net.orionlab.sputniknchatserver.db.tables.records.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.`val`
import org.jooq.impl.DSL.inline
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource
import net.orionlab.sputniknchatserver.db.tables.RoomEventMessage as TRoomEventMessage
import net.orionlab.sputniknchatserver.db.tables.RoomEventMessageReaction as TRoomEventMessageReaction
import net.orionlab.sputniknchatserver.db.tables.RoomEventMessageAttachment as TRoomEventMessageAttachment
import net.orionlab.sputniknchatserver.db.tables.RoomEventSystem as TRoomEventSystem
import net.orionlab.sputniknchatserver.db.tables.ChatAttachment as TChatAttachment

interface RoomDao {
    fun addRoomEventMessage(
        pojo: net.orionlab.sputniknchatserver.db.tables.pojos.RoomEventMessage,
        attachmentIds: List<UUID>
    ): RoomEventMessageWithAttachmentEntity?

    fun addRoom(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.Room, creatorUserId: UUID, memberIds: Set<UUID>): net.orionlab.sputniknchatserver.db.tables.records.RoomRecord?
    fun getRooms(): List<net.orionlab.sputniknchatserver.db.tables.records.RoomRecord>

    /**
     * @return list of added users
     */
    fun addRoomMembers(roomId: UUID, memberIds: List<UUID>): List<RoomMemberEntity>
    fun getRoomMembers(roomId: UUID): List<RoomMemberEntity>
    fun setRoomMemberStatus(roomId: UUID, memberStatus: Map<UUID, net.orionlab.sputniknchatserver.db.enums.MemberStatus>): Int
    fun setMemberReadMarker(roomId: UUID, userId: UUID, readMarker: LocalDateTime): Boolean
    fun getRoomEvents(
        roomId: UUID,
        eventType: RoomEventType,
        eventLimit: Int,
        sinceTime: LocalDateTime,
        sinceTimeOrder: SinceTimeOrderType
    ): RoomEventsEntity

    fun getMemberUnreads(roomId: UUID): List<RoomMemberUnreadEntity>
}

class RoomDaoImpl(ds: DataSource, dialect: SQLDialect) : BaseDao(ds, dialect), RoomDao {
    override fun addRoomEventMessage(
        pojo: net.orionlab.sputniknchatserver.db.tables.pojos.RoomEventMessage,
        attachmentIds: List<UUID>
    ): RoomEventMessageWithAttachmentEntity? {
        return try {
            val dsl = DSL.using(ds, dialect)

            val result = dsl.insertInto(
                TRoomEventMessage.ROOM_EVENT_MESSAGE,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.ROOM_ID,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.USER_ID,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.CONTENT,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.VERSION,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.CLIENT_EVENT_ID,
            )
                .values(
                    pojo.roomId,
                    pojo.userId,
                    pojo.content,
                    pojo.version,
                    pojo.clientEventId
                )
                .returningResult(TRoomEventMessage.ROOM_EVENT_MESSAGE.fields().toList())
                .fetchOneInto(net.orionlab.sputniknchatserver.db.tables.records.RoomEventMessageRecord::class.java)
            val attachments = result?.let { record ->
                if (attachmentIds.isNotEmpty()) {
                    val attachmentInserts = attachmentIds.map {
                        dsl.insertInto(
                            TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT,
                            TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.ROOM_EVENT_MESSAGE_ID,
                            TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.CHAT_ATTACHMENT_ID
                        )
                            .values(record.id, it)
                    }
                    dsl.batch(attachmentInserts).execute()
                    dsl.select(
                        TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.ID,
                        TChatAttachment.CHAT_ATTACHMENT.ID,
                        TChatAttachment.CHAT_ATTACHMENT.MIME_TYPE,
                    )
                        .from(TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT)
                        .innerJoin(TChatAttachment.CHAT_ATTACHMENT)
                        .on(
                            TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.CHAT_ATTACHMENT_ID.eq(
                                TChatAttachment.CHAT_ATTACHMENT.ID
                            )
                        )
                        .where(TChatAttachment.CHAT_ATTACHMENT.ID.`in`(attachmentIds))
                        .fetch()
                        .map { rec ->
                            ChatAttachmentDetail(
                                rec.value1().toString(),
                                rec.value2().toString(),
                                rec.value3()
                            )
                        }
                } else {
                    emptyList()
                }
            } ?: emptyList()
            result?.let {
                RoomEventMessageWithAttachmentEntity(it, attachments)
            }
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }

    override fun addRoom(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.Room, creatorUserId: UUID, memberIds: Set<UUID>): net.orionlab.sputniknchatserver.db.tables.records.RoomRecord? {
        return try {
            val dsl = DSL.using(ds, dialect)
            val room = dsl
                .insertInto(TRoom.ROOM, TRoom.ROOM.TITLE, TRoom.ROOM.AVATAR)
                .values(pojo.title, pojo.avatar)
                .returningResult(TRoom.ROOM.fields().toList())
                .fetchOneInto(net.orionlab.sputniknchatserver.db.tables.records.RoomRecord::class.java)
            room?.id?.let { roomId ->
                val memberRecords = memberIds.map { memberId ->
                    dsl.insertInto(
                        TRoomMember.ROOM_MEMBER,
                        TRoomMember.ROOM_MEMBER.ROOM_ID,
                        TRoomMember.ROOM_MEMBER.USER_ID,
                        TRoomMember.ROOM_MEMBER.MEMBER_STATUS,
                        TRoomMember.ROOM_MEMBER.PERMISSION,
                    )
                        .values(
                            roomId,
                            memberId,
                            net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_INVITED,
                            0.toShort()
                        )
                }
                dsl.batch(memberRecords).execute()
                val systemMessages = memberIds.map { member ->
                    dsl.insertInto(
                        TRoomEventSystem.ROOM_EVENT_SYSTEM,
                        TRoomEventSystem.ROOM_EVENT_SYSTEM.ROOM_ID,
                        TRoomEventSystem.ROOM_EVENT_SYSTEM.CONTENT,
                        TRoomEventSystem.ROOM_EVENT_SYSTEM.VERSION,
                    )
                        .values(
                            roomId,
                            RoomEventSystemContentV1(
                                action = RoomEventSystemActionTypeV1.UserInvite,
                                srcUserId = creatorUserId.toString(),
                                dstUserId = member.toString(),
                            ).serialize(),
                            RoomEventSystemContentV1.version
                        )
                }
                dsl.batch(systemMessages).execute()
            }
            room
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }


    override fun getRooms(): List<net.orionlab.sputniknchatserver.db.tables.records.RoomRecord> {
        return try {
            DSL.using(ds, dialect).selectFrom(TRoom.ROOM)
                .fetch().toList()
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

    override fun addRoomMembers(roomId: UUID, memberIds: List<UUID>): List<RoomMemberEntity> {
        return try {
            val dsl = DSL.using(ds, dialect)
            // find existing users but still not in roomMembers
            val possibleUsers = dsl.select(TUser.USER.fields().toList())
                .from(TUser.USER)
                .innerJoin(TRoomMember.ROOM_MEMBER).on(TRoomMember.ROOM_MEMBER.USER_ID.notEqual(TUser.USER.ID))
                .where(
                    TUser.USER.ID.`in`(memberIds)
                        .and(TRoomMember.ROOM_MEMBER.ROOM_ID.eq(roomId))
                )
                .fetchInto(net.orionlab.sputniknchatserver.db.tables.records.UserRecord::class.java)
            val inserts = possibleUsers.map { user ->
                dsl.insertInto(
                    TRoomMember.ROOM_MEMBER,
                    TRoomMember.ROOM_MEMBER.ROOM_ID,
                    TRoomMember.ROOM_MEMBER.USER_ID,
                    TRoomMember.ROOM_MEMBER.MEMBER_STATUS,
                    TRoomMember.ROOM_MEMBER.PERMISSION
                )
                    .values(
                        roomId,
                        user.id,
                        net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_INVITED,
                        0
                    )
            }
            dsl.batch(inserts).execute()
            possibleUsers.map {
                RoomMemberEntity(
                    userId = it.id,
                    fullName = it.fullName,
                    memberStatus = net.orionlab.sputniknchatserver.db.enums.MemberStatus.MEMBER_STATUS_INVITED,
                    avatar = it.avatar,
                    lastReadMarker = null
                )
            }
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

    override fun getRoomMembers(roomId: UUID): List<RoomMemberEntity> {
        return try {
            DSL.using(ds, dialect)
                .select(
                    TUser.USER.ID,
                    TUser.USER.FULL_NAME,
                    TUser.USER.AVATAR,
                    TRoomMember.ROOM_MEMBER.MEMBER_STATUS,
                    TRoomMember.ROOM_MEMBER.LAST_READ_MARKER
                )
                .from(TRoomMember.ROOM_MEMBER)
                .innerJoin(TRoom.ROOM)
                .on(TRoomMember.ROOM_MEMBER.ROOM_ID.eq(TRoom.ROOM.ID))
                .innerJoin(TUser.USER)
                .on(TRoomMember.ROOM_MEMBER.USER_ID.eq(TUser.USER.ID))
                .where(TRoom.ROOM.ID.eq(roomId))
                .fetch()
                .map {
                    RoomMemberEntity(
                        userId = it.value1(),
                        fullName = it.value2(),
                        avatar = it.value3(),
                        memberStatus = it.value4(),
                        lastReadMarker = it.value5()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
                    )
                }
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

    override fun setRoomMemberStatus(roomId: UUID, memberStatus: Map<UUID, net.orionlab.sputniknchatserver.db.enums.MemberStatus>): Int {
        return try {
            val dsl = DSL.using(ds, dialect)
            val updates = memberStatus.map { (memberId, status) ->
                dsl.update(TRoomMember.ROOM_MEMBER)
                    .set(TRoomMember.ROOM_MEMBER.MEMBER_STATUS, status)
                    .where(
                        TRoomMember.ROOM_MEMBER.ROOM_ID.eq(roomId).and(
                            TRoomMember.ROOM_MEMBER.USER_ID.eq(memberId)
                        )
                    )
            }
            dsl.batch(updates).execute().size
        } catch (ex: Throwable) {
            logger.error("", ex)
            0
        }
    }

    override fun setMemberReadMarker(roomId: UUID, userId: UUID, readMarker: LocalDateTime): Boolean {
        return try {
            val result = DSL.using(ds, dialect).update(TRoomMember.ROOM_MEMBER)
                .set(TRoomMember.ROOM_MEMBER.LAST_READ_MARKER, readMarker)
                .where(
                    TRoomMember.ROOM_MEMBER.ROOM_ID.eq(roomId)
                        .and(TRoomMember.ROOM_MEMBER.USER_ID.eq(userId))
                )
                .execute()
            result > 0
        } catch (ex: Throwable) {
            logger.error("", ex)
            false
        }
    }

    override fun getRoomEvents(
        roomId: UUID,
        eventType: RoomEventType,
        eventLimit: Int,
        sinceTime: LocalDateTime,
        sinceTimeOrder: SinceTimeOrderType
    ): RoomEventsEntity {
        return try {
            val dsl = DSL.using(ds, dialect)
            val falseCondition = DSL.condition("1=0")
            val hasMessageCondition =
                listOf(RoomEventType.RoomEventTypeMessage, RoomEventType.RoomEventTypeAll).contains(eventType)
            val messageLimit = if (hasMessageCondition) eventLimit else 0
            val tmpMessageEventIds =
                dsl.select(TRoomEventMessage.ROOM_EVENT_MESSAGE.ID, TRoomEventMessage.ROOM_EVENT_MESSAGE.DATE_CREATE)
                    .from(TRoomEventMessage.ROOM_EVENT_MESSAGE)
                    .where(
                        if (hasMessageCondition) {
                            when (sinceTimeOrder) {
                                SinceTimeOrderType.SinceTimeOrderTypeNewest -> TRoomEventMessage.ROOM_EVENT_MESSAGE.DATE_CREATE.greaterThan(
                                    sinceTime
                                )
                                SinceTimeOrderType.SinceTimeOrderTypeOldest -> TRoomEventMessage.ROOM_EVENT_MESSAGE.DATE_CREATE.lessThan(
                                    sinceTime
                                )
                            }
                        } else {
                            falseCondition
                        }
                    )
                    .orderBy(TRoomEventMessage.ROOM_EVENT_MESSAGE.DATE_CREATE.desc())
                    .limit(messageLimit)

            val hasSystemCondition =
                listOf(RoomEventType.RoomEventTypeSystem, RoomEventType.RoomEventTypeAll).contains(eventType)
            val systemLimit = if (hasSystemCondition) eventLimit else 0
            val tmpSystemEventIds =
                dsl.select(TRoomEventSystem.ROOM_EVENT_SYSTEM.ID, TRoomEventSystem.ROOM_EVENT_SYSTEM.DATE_CREATE)
                    .from(TRoomEventSystem.ROOM_EVENT_SYSTEM)
                    .where(
                        if (hasSystemCondition) {
                            when (sinceTimeOrder) {
                                SinceTimeOrderType.SinceTimeOrderTypeNewest -> TRoomEventSystem.ROOM_EVENT_SYSTEM.DATE_CREATE.greaterThan(
                                    sinceTime
                                )
                                SinceTimeOrderType.SinceTimeOrderTypeOldest -> TRoomEventSystem.ROOM_EVENT_SYSTEM.DATE_CREATE.lessThan(
                                    sinceTime
                                )
                            }
                        } else {
                            falseCondition
                        }
                    )
                    .orderBy(TRoomEventSystem.ROOM_EVENT_SYSTEM.DATE_CREATE.desc())
                    .limit(systemLimit)

            val eventIds = tmpMessageEventIds
                .union(tmpSystemEventIds)
                .orderBy(inline(2).desc())
                .limit(eventLimit)
                .fetch()

            val messageEvents = dsl.selectFrom(TRoomEventMessage.ROOM_EVENT_MESSAGE)
                .where(
                    TRoomEventMessage.ROOM_EVENT_MESSAGE.ID.`in`(eventIds.map { it.value1() }.toList())
                        .and(
                            TRoomEventMessage.ROOM_EVENT_MESSAGE.ROOM_ID.eq(roomId)
                        )
                ).fetch()
            val attachmentEvents =
                dsl.select(
                    TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.ROOM_EVENT_MESSAGE_ID,
                    TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.ID,
                    TChatAttachment.CHAT_ATTACHMENT.ID,
                    TChatAttachment.CHAT_ATTACHMENT.MIME_TYPE
                )
                    .from(TChatAttachment.CHAT_ATTACHMENT)
                    .innerJoin(TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT)
                    .on(TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.CHAT_ATTACHMENT_ID.eq(TChatAttachment.CHAT_ATTACHMENT.ID))
                    .where(
                        TRoomEventMessageAttachment.ROOM_EVENT_MESSAGE_ATTACHMENT.ROOM_EVENT_MESSAGE_ID.`in`(
                            messageEvents.map { it.value1() })
                    ).fetch()
                    .map {
                        RoomEventAttachmentEntity(
                            it.value1().toString(),
                            ChatAttachmentDetail(
                                it.value2().toString(),
                                it.value3().toString(),
                                it.value4()
                            )
                        )
                    }

            val reactionEvents = dsl.select(
                TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.ROOM_EVENT_MESSAGE_ID,
                TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.ID,
                TRoomEventMessage.ROOM_EVENT_MESSAGE.ROOM_ID,
                TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.USER_ID,
                TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.CONTENT,
                TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.DATE_CREATE,
            )
                .from(TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION)
                .innerJoin(TRoomEventMessage.ROOM_EVENT_MESSAGE)
                .on(TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.ROOM_EVENT_MESSAGE_ID.eq(TRoomEventMessage.ROOM_EVENT_MESSAGE.ID))
                .where(
                    TRoomEventMessageReaction.ROOM_EVENT_MESSAGE_REACTION.ROOM_EVENT_MESSAGE_ID.`in`(messageEvents.map { it.value1() })
                        .and(
                            TRoomEventMessage.ROOM_EVENT_MESSAGE.ROOM_ID.eq(roomId)
                        )
                ).fetch()
                .map {
                    RoomEventReactionEntity(
                        eventId = it.value1().toString(),
                        detail = RoomEventReactionDetail(
                            eventId = it.value2().toString(),
                            roomId = it.value3().toString(),
                            senderId = it.value4().toString(),
                            content = it.value5(),
                            timestamp = it.value6().toInstant(ZoneOffset.UTC).toEpochMilli()
                        )
                    )
                }

            val systemEvents = dsl.selectFrom(TRoomEventSystem.ROOM_EVENT_SYSTEM)
                .where(
                    TRoomEventSystem.ROOM_EVENT_SYSTEM.ID.`in`(eventIds.map { it.value1() }.toList())
                        .and(
                            TRoomEventSystem.ROOM_EVENT_SYSTEM.ROOM_ID.eq(roomId)
                        )
                ).fetch()

            RoomEventsEntity(messageEvents, attachmentEvents, reactionEvents, systemEvents)
        } catch (ex: Throwable) {
            logger.error("", ex)
            RoomEventsEntity(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    override fun getMemberUnreads(roomId: UUID): List<RoomMemberUnreadEntity> {
        val query = """
        select eventMsg.user_id, eventMsg.count, eventSys.count
        from
        (select count(rem.id), rm.${TRoomMember.ROOM_MEMBER.USER_ID.name}
        from ${TRoomMember.ROOM_MEMBER.name} rm
        inner join ${TRoom.ROOM.name} r on rm.${TRoomMember.ROOM_MEMBER.ROOM_ID.name} = r.id
        left join ${TRoomEventMessage.ROOM_EVENT_MESSAGE.name} rem on rem.${TRoomEventMessage.ROOM_EVENT_MESSAGE.ROOM_ID.name} = r.id 
            AND rem.${TRoomEventMessage.ROOM_EVENT_MESSAGE.DATE_CREATE.name} > coalesce(rm.${TRoomMember.ROOM_MEMBER.LAST_READ_MARKER.name}, '1970-01-01')
        where
        r.id = {0}
        group by r.${TRoom.ROOM.ID.name}, rm.${TRoomMember.ROOM_MEMBER.USER_ID.name}) eventMsg
        JOIN (select count(res.id), rm.${TRoomMember.ROOM_MEMBER.USER_ID.name}
        from ${TRoomMember.ROOM_MEMBER.name} rm
        inner join ${TRoom.ROOM.name} r on rm.${TRoomMember.ROOM_MEMBER.ROOM_ID.name} = r.id
        left join ${TRoomEventSystem.ROOM_EVENT_SYSTEM.name} res on res.${TRoomEventSystem.ROOM_EVENT_SYSTEM.ROOM_ID.name} = r.id 
            AND res.${TRoomEventSystem.ROOM_EVENT_SYSTEM.DATE_CREATE.name} > coalesce(rm.${TRoomMember.ROOM_MEMBER.LAST_READ_MARKER.name}, '1970-01-01')
        where
        r.id = {0}
        group by r.${TRoom.ROOM.ID.name}, rm.${TRoomMember.ROOM_MEMBER.USER_ID.name}) eventSys ON eventMsg.user_id = eventSys.user_id
        """.trimIndent()
        return try {
            val dsl = DSL.using(ds, dialect)
            dsl.resultQuery(query, `val`(roomId))
                .fetch()
                .map { result ->
                    RoomMemberUnreadEntity(
                        result.get(0) as UUID,
                        (result.get(1) as Long).toInt(),
                        (result.get(2) as Long).toInt(),
                    )
                }
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }
}