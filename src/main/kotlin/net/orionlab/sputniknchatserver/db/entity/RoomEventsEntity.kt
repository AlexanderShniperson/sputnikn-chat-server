package net.orionlab.sputniknchatserver.db.entity

data class RoomEventsEntity(
    val messageEvents: List<net.orionlab.sputniknchatserver.db.tables.records.RoomEventMessageRecord>,
    val attachmentEvents: List<RoomEventAttachmentEntity>,
    val reactionEvents: List<RoomEventReactionEntity>,
    val systemEvents: List<net.orionlab.sputniknchatserver.db.tables.records.RoomEventSystemRecord>
)