package net.orionlab.sputniknchatserver.db.entity

import net.orionlab.sputniknchatserver.actor.ChatAttachmentDetail

data class RoomEventAttachmentEntity(
    val eventId: String,
    val detail: ChatAttachmentDetail
)