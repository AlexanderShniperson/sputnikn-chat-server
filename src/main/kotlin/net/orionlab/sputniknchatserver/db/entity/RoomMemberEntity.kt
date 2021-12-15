package net.orionlab.sputniknchatserver.db.entity

import java.util.*

data class RoomMemberEntity(
    val userId: UUID,
    var fullName: String,
    var memberStatus: net.orionlab.sputniknchatserver.db.enums.MemberStatus,
    var avatar: String?,
    var lastReadMarker: Long?
)