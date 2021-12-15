package net.orionlab.sputniknchatserver.db.entity

import net.orionlab.sputniknchatserver.actor.ChatAttachmentDetail

class RoomEventMessageWithAttachmentEntity(
    val event: net.orionlab.sputniknchatserver.db.tables.records.RoomEventMessageRecord,
    val attachments: List<ChatAttachmentDetail>
)