package net.orionlab.sputniknchatserver.db

import org.jooq.SQLDialect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

abstract class BaseDao(
    protected val ds: DataSource,
    protected val dialect: SQLDialect
) {
    protected val logger: Logger = LoggerFactory.getLogger(this.javaClass.canonicalName)
}