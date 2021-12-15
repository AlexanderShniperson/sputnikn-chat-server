package net.orionlab.sputniknchatserver.util

import java.util.*

fun String.parseUUIDSafe(): UUID? {
    return try {
        UUID.fromString(this)
    } catch (ex: Throwable) {
        null
    }
}