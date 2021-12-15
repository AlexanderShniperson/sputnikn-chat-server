package net.orionlab.sputniknchatserver.db.entity

import net.orionlab.sputniknchatserver.actor.RoomEventReactionDetail

data class RoomEventReactionEntity(
    val eventId: String,
    val detail: RoomEventReactionDetail
)