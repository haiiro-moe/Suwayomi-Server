package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.ChapterTable

object UserChapterStateTable : IntIdTable("user_chapter_state") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val chapter = reference("chapter_id", ChapterTable, onDelete = ReferenceOption.CASCADE)
    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)
    val lastReadAt = long("last_read_at").default(0)

    init {
        uniqueIndex(user, chapter)
        index(false, user, isRead)
        index(false, user, lastReadAt)
    }
}
