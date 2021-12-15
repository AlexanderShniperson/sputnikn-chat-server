package net.orionlab.common

import com.google.protobuf.GeneratedMessageV3

interface SerializerBase {
    fun bytesToMessage(msgId: Int, bytes: ByteArray): GeneratedMessageV3?
    fun messageToId(message: GeneratedMessageV3): Int
}