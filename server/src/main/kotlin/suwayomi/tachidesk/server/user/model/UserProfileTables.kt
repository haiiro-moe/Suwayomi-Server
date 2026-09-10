package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.MangaTable

object UserProfileTable : IntIdTable("user_profiles") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val description = varchar("description", 2000).default("")
}

object UserFavoriteTable : IntIdTable("user_favorites") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(user, manga)
    }
}
