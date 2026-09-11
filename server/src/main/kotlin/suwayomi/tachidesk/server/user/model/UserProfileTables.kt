package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.MangaTable

object UserProfileTable : IntIdTable("user_profiles") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val description = varchar("description", 2000).default("")
    val bannerUrl = varchar("banner_url", 2048).nullable()
}

object UserFavoriteTable : IntIdTable("user_favorites") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(user, manga)
    }
}

object UserMangaNoteTable : IntIdTable("user_manga_notes") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)
    val note = text("note")

    init {
        uniqueIndex(user, manga)
    }
}
