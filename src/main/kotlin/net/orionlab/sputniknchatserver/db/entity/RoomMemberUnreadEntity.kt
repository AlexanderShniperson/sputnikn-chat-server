package net.orionlab.sputniknchatserver.db.entity

import java.util.*

data class RoomMemberUnreadEntity(
    val memberId: UUID,
    val eventMessageUnread: Int,
    val eventSystemUnread: Int
){
    override fun hashCode(): Int {
        return memberId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoomMemberUnreadEntity

        return memberId == other.memberId
    }
}