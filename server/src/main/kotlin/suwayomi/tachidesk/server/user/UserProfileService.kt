package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserFavoriteTable
import suwayomi.tachidesk.server.user.model.UserProfileTable

object UserProfileService {
    fun description(userId: Int): String = transaction(DBManager.db) {
        UserProfileTable.selectAll().where { UserProfileTable.user eq userId }.firstOrNull()
            ?.get(UserProfileTable.description) ?: ""
    }

    fun updateDescription(userId: Int, description: String) {
        require(description.length <= 2000) { "Profile description is too long" }
        transaction(DBManager.db) {
            val existing = UserProfileTable.selectAll().where { UserProfileTable.user eq userId }.firstOrNull()
            if (existing == null) {
                UserProfileTable.insertIgnore {
                    it[user] = userId
                    it[UserProfileTable.description] = description
                }
            } else {
                UserProfileTable.update({ UserProfileTable.user eq userId }) {
                    it[UserProfileTable.description] = description
                }
            }
        }
    }

    fun addFavorite(userId: Int, mangaId: Int) {
        CategoryAccessService.requireReadableManga(userId, listOf(mangaId))
        transaction(DBManager.db) {
            UserFavoriteTable.insertIgnore {
                it[user] = userId
                it[manga] = mangaId
            }
        }
    }

    fun removeFavorite(userId: Int, mangaId: Int) {
        transaction(DBManager.db) {
            UserFavoriteTable.deleteWhere {
                (UserFavoriteTable.user eq userId) and (UserFavoriteTable.manga eq mangaId)
            }
        }
    }

    fun favoriteMangaIds(userId: Int): List<Int> = transaction(DBManager.db) {
        val visible = CategoryAccessService.readableMangaIds(userId)
        if (visible.isEmpty()) return@transaction emptyList()
        UserFavoriteTable
            .select(UserFavoriteTable.manga)
            .where { (UserFavoriteTable.user eq userId) and (UserFavoriteTable.manga inList visible) }
            .map { it[UserFavoriteTable.manga].value }
    }
}
