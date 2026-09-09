package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserChapterStateTable
import java.time.Instant

data class UserChapterState(
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
)

object UserChapterStateService {
    fun get(userId: Int, chapterId: Int): UserChapterState? =
        transaction(DBManager.db) {
            UserChapterStateTable
                .selectAll()
                .where {
                    (UserChapterStateTable.user eq userId) and
                        (UserChapterStateTable.chapter eq chapterId)
                }.firstOrNull()
                ?.toState()
        }

    fun getOrLegacy(userId: Int, chapterId: Int): UserChapterState =
        get(userId, chapterId) ?: transaction(DBManager.db) {
            ChapterTable
                .selectAll()
                .where { ChapterTable.id eq chapterId }
                .firstOrNull()
                ?.let {
                    UserChapterState(
                        it[ChapterTable.isRead],
                        it[ChapterTable.isBookmarked],
                        it[ChapterTable.lastPageRead],
                        it[ChapterTable.lastReadAt],
                    )
                }
        } ?: UserChapterState(false, false, 0, 0)

    fun update(userId: Int, chapterId: Int, read: Boolean?, bookmarked: Boolean?, lastPageRead: Int?) {
        transaction(DBManager.db) {
            val existing =
                UserChapterStateTable
                    .selectAll()
                    .where {
                        (UserChapterStateTable.user eq userId) and
                            (UserChapterStateTable.chapter eq chapterId)
                    }.firstOrNull()
            val current = existing?.toState() ?: getOrLegacy(userId, chapterId)
            val now = if (lastPageRead != null || read != null) Instant.now().epochSecond else current.lastReadAt
            if (existing == null) {
                UserChapterStateTable.insert {
                    it[user] = userId
                    it[chapter] = chapterId
                    it[isRead] = read ?: current.isRead
                    it[isBookmarked] = bookmarked ?: current.isBookmarked
                    it[UserChapterStateTable.lastPageRead] = lastPageRead ?: current.lastPageRead
                    it[lastReadAt] = now
                }
            } else {
                UserChapterStateTable.update({ UserChapterStateTable.id eq existing[UserChapterStateTable.id] }) {
                    read?.let { value -> it[isRead] = value }
                    bookmarked?.let { value -> it[isBookmarked] = value }
                    lastPageRead?.let { value -> it[UserChapterStateTable.lastPageRead] = value }
                    if (read != null || lastPageRead != null) it[lastReadAt] = now
                }
            }
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toState() =
        UserChapterState(
            this[UserChapterStateTable.isRead],
            this[UserChapterStateTable.isBookmarked],
            this[UserChapterStateTable.lastPageRead],
            this[UserChapterStateTable.lastReadAt],
        )
}
