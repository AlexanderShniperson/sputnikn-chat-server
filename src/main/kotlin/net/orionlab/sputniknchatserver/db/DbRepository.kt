package net.orionlab.sputniknchatserver.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.SQLDialect

interface DbRepository {
    fun getRoomDao(): RoomDao
    fun getUserDao(): UserDao
    fun getChatAttachmentDao(): ChatAttachmentDao
    fun close()
}

class DbRepositoryImpl(
    config: HikariConfig
) : DbRepository {
    private val dataSource = HikariDataSource(config)
    private val dialect = SQLDialect.POSTGRES
    private val roomDao = RoomDaoImpl(dataSource, dialect)
    private val userDao = UserDaoImpl(dataSource, dialect)
    private val chatAttachmentDao = ChatAttachmentDaoImpl(dataSource, dialect)

    override fun getRoomDao(): RoomDao = roomDao
    override fun getUserDao(): UserDao = userDao
    override fun getChatAttachmentDao(): ChatAttachmentDao = chatAttachmentDao

    override fun close() {
        dataSource.connection.close()
    }
}