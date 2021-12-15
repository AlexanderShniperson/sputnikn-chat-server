package net.orionlab.sputniknchatserver.db.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Suppress("unused")
@Serializable
enum class RoomEventSystemActionTypeV1 {
    @SerialName("change_avatar")
    ChangeAvatar,

    @SerialName("change_title")
    ChangeTitle,

    @SerialName("user_invite")
    UserInvite,

    @SerialName("user_join")
    UserJoin,

    @SerialName("user_leave")
    UserLeave,

    @SerialName("user_kick")
    UserLick,

    @SerialName("user_ban")
    UserBan
}

@Suppress("unused")
@Serializable
data class RoomEventSystemContentV1(
    val action: RoomEventSystemActionTypeV1,
    val srcUserId: String,
    val dstUserId: String? = null,
    val fromContent: String? = null,
    val toContent: String? = null

) {
    companion object {
        const val version = 1.toShort()

        fun deserialize(value: String): RoomEventSystemContentV1 {
            return Json.decodeFromString(value)
        }
    }

    fun serialize(): String {
        return Json.encodeToString(this)
    }
}
