package net.orionlab.sputniknchatserver.routing

import net.orionlab.sputniknchatserver.db.DbRepository
import net.orionlab.sputniknchatserver.routing.response.ChatAttachmentResponse
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.File
import java.util.*

fun Route.restRouting(dbRepository: DbRepository, mediaPath: String) {
     val headerUser = "x-sputnikn-user"

    get("/media/{attachmentId}") {
        val tmpUserId = call.request.header(headerUser)
            ?: throw Exception("Wrong user identifier!")

        val userId = try {
            UUID.fromString(tmpUserId)
        } catch (ex: Throwable) {
            null
        } ?: throw Exception("Wrong user identifier!")

        dbRepository.getUserDao().findUserById(userId)
            ?: throw Exception("User not found!")

        val tmpAttachmentId = call.parameters["attachmentId"]
        if (tmpAttachmentId == null || tmpAttachmentId.isEmpty()) {
            throw Exception("Resource not found #1.")
        }
        val attachmentId = try {
            UUID.fromString(tmpAttachmentId)
        } catch (ex: Throwable) {
            null
        } ?: throw Exception("Resource not found #2.")

        val chatAttachment = dbRepository.getChatAttachmentDao().getChatAttachment(attachmentId)
            ?: throw Exception("Resource not found #3.")

        val attachmentFile = File("$mediaPath/${attachmentId}")

        if (!attachmentFile.exists()) {
            throw Exception("Resource not found #4.")
        }

        /**
         * By default, if called from the browser, this will cause the file to be viewed inline.
         * If you instead want to prompt the browser to download the file, you can include the Content-Disposition header.
         */
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                attachmentId.toString()
            )
                .toString()
        )
        call.respond(
            LocalFileContent(
                file = attachmentFile,
                contentType = ContentType.parse(chatAttachment.mimeType)
            )
        )
    }
    post("/media") {
        val tmpUserId = call.request.header(headerUser)
            ?: throw Exception("Wrong user identifier!")

        val userId = try {
            UUID.fromString(tmpUserId)
        } catch (ex: Throwable) {
            null
        } ?: throw Exception("Wrong user identifier!")

        val user = dbRepository.getUserDao().findUserById(userId)
            ?: throw Exception("User not found!")

        // retrieve all multipart data (suspending)
        val multipart = call.receiveMultipart()
        val attachmentIds = mutableListOf<String>()
        multipart.forEachPart { part ->
            // if part is a file (could be form item)
            if (part is PartData.FileItem) {
                // retrieve file name of upload
                val mimeType = part.contentType ?: ContentType.Application.OctetStream
                val attachmentName = UUID.randomUUID()
                val file = File("$mediaPath/$attachmentName")

                // use InputStream from part to save file
                part.streamProvider().use { its ->
                    // copy the stream to the file with buffering
                    file.outputStream().buffered().use {
                        // note that this is blocking
                        its.copyTo(it)
                    }
                }
                dbRepository.getChatAttachmentDao().addChatAttachment(
                    net.orionlab.sputniknchatserver.db.tables.pojos.ChatAttachment(
                        attachmentName,
                        user.id,
                        mimeType.toString(),
                        null
                    )
                )?.let {
                    attachmentIds.add(attachmentName.toString())
                }
            }
            // make sure to dispose of the part after use, to prevent leaks
            part.dispose()
        }
        call.respond(ChatAttachmentResponse(attachmentIds))
    }
}