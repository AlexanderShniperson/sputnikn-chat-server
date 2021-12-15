package net.orionlab.sputniknchatserver.routing.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatAttachmentResponse(
    @JsonProperty("attachments")
    val attachments: List<String>
)