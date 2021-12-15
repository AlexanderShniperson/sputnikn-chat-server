package net.orionlab.sputniknchatserver.db

import net.orionlab.sputniknchatserver.db.tables.Room as TRoom
import net.orionlab.sputniknchatserver.db.tables.RoomMember as TRoomMember
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.*
import javax.sql.DataSource
import net.orionlab.sputniknchatserver.db.tables.User as TUser

interface UserDao {
    fun addUser(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.User): net.orionlab.sputniknchatserver.db.tables.records.UserRecord?
    fun findUserByLoginPassword(login: String, password: String): net.orionlab.sputniknchatserver.db.tables.records.UserRecord?
    fun findUsers(memberIds: Set<UUID>): List<net.orionlab.sputniknchatserver.db.tables.records.UserRecord>
    fun findUserRooms(userId: UUID): List<net.orionlab.sputniknchatserver.db.tables.records.RoomRecord>
    fun findUserById(userId: UUID): net.orionlab.sputniknchatserver.db.tables.records.UserRecord?
    fun getAllUsers(): List<net.orionlab.sputniknchatserver.db.tables.records.UserRecord>
}

class UserDaoImpl(ds: DataSource, dialect: SQLDialect) : BaseDao(ds, dialect), UserDao {
    override fun addUser(pojo: net.orionlab.sputniknchatserver.db.tables.pojos.User): net.orionlab.sputniknchatserver.db.tables.records.UserRecord? {
        return try {
            DSL.using(ds, dialect)
                .insertInto(
                    TUser.USER,
                    TUser.USER.LOGIN,
                    TUser.USER.PASSWORD,
                    TUser.USER.FULL_NAME,
                    TUser.USER.AVATAR
                )
                .values(pojo.login, pojo.password, pojo.fullName, pojo.avatar)
                .returningResult(TUser.USER.fields().toList())
                .fetchOneInto(net.orionlab.sputniknchatserver.db.tables.records.UserRecord::class.java)
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }

    override fun findUserByLoginPassword(login: String, password: String): net.orionlab.sputniknchatserver.db.tables.records.UserRecord? {
        return try {
            DSL.using(ds, dialect).selectFrom(TUser.USER)
                .where(TUser.USER.LOGIN.eq(login).and(TUser.USER.PASSWORD.eq(password)))
                .fetchOne()
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }


    override fun findUsers(memberIds: Set<UUID>): List<net.orionlab.sputniknchatserver.db.tables.records.UserRecord> {
        return try {
            DSL.using(ds, dialect).selectFrom(TUser.USER)
                .where(TUser.USER.ID.`in`(memberIds))
                .fetch()
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

    override fun findUserRooms(userId: UUID): List<net.orionlab.sputniknchatserver.db.tables.records.RoomRecord> {
        return try {
            DSL.using(ds, dialect)
                .select(TRoom.ROOM.fields().toList())
                .from(TRoom.ROOM)
                .innerJoin(TRoomMember.ROOM_MEMBER)
                .on(TRoomMember.ROOM_MEMBER.ROOM_ID.eq(TRoom.ROOM.ID))
                .innerJoin(TUser.USER)
                .on(TRoomMember.ROOM_MEMBER.USER_ID.eq(TUser.USER.ID))
                .where(TUser.USER.ID.eq(userId))
                .fetchInto(net.orionlab.sputniknchatserver.db.tables.records.RoomRecord::class.java)
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

    override fun findUserById(userId: UUID): net.orionlab.sputniknchatserver.db.tables.records.UserRecord? {
        return try {
            DSL.using(ds, dialect)
                .selectFrom(TUser.USER)
                .where(TUser.USER.ID.eq(userId))
                .fetchOne()
        } catch (ex: Throwable) {
            logger.error("", ex)
            null
        }
    }

    override fun getAllUsers(): List<net.orionlab.sputniknchatserver.db.tables.records.UserRecord> {
        return try {
            DSL.using(ds, dialect).selectFrom(TUser.USER)
                .fetch()
        } catch (ex: Throwable) {
            logger.error("", ex)
            emptyList()
        }
    }

}