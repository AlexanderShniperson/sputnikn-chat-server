package net.orionlab.sputniknchatserver.db

import net.orionlab.sputniknchatserver.db.tables.ChatAttachment as TChatAttachment
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.*
import javax.sql.DataSource

interface ChatAttachmentDao {
    fun getChatAttachment(attachmentId: UUID): net.orionlab.sputniknchatserver.db.tables.records.ChatAttachmentRecord?
    fun addChatAttachment(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.ChatAttachment): net.orionlab.sputniknchatserver.db.tables.records.ChatAttachmentRecord?
}

class ChatAttachmentDaoImpl(ds: DataSource, dialect: SQLDialect) : BaseDao(ds, dialect), ChatAttachmentDao {
    override fun getChatAttachment(attachmentId: UUID): net.orionlab.sputniknchatserver.db.tables.records.ChatAttachmentRecord? {
        return try {
            DSL.using(ds, dialect)
                .selectFrom(TChatAttachment.CHAT_ATTACHMENT)
                .where(TChatAttachment.CHAT_ATTACHMENT.ID.eq(attachmentId))
                .fetchOne()
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }

    override fun addChatAttachment(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.ChatAttachment): net.orionlab.sputniknchatserver.db.tables.records.ChatAttachmentRecord? {
        return try {
            DSL.using(ds, dialect)
                .insertInto(
                    TChatAttachment.CHAT_ATTACHMENT,
                    TChatAttachment.CHAT_ATTACHMENT.ID,
                    TChatAttachment.CHAT_ATTACHMENT.USER_ID,
                    TChatAttachment.CHAT_ATTACHMENT.MIME_TYPE
                )
                .values(
                    pojo.id ?: UUID.randomUUID(),
                    pojo.userId,
                    pojo.mimeType
                )
                .returningResult(TChatAttachment.CHAT_ATTACHMENT.fields().toList())
                .fetchOneInto(net.orionlab.sputniknchatserver.db.tables.records.ChatAttachmentRecord::class.java)
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }


}