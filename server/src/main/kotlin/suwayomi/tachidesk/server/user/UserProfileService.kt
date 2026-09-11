package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserFavoriteTable
import suwayomi.tachidesk.server.user.model.UserProfileTable
import suwayomi.tachidesk.server.user.model.UserTable

object UserProfileService {
    fun description(userId: Int): String = transaction(DBManager.db) {
        UserProfileTable.selectAll().where { UserProfileTable.user eq userId }.firstOrNull()
            ?.get(UserProfileTable.description) ?: ""
    }

    fun updateProfile(userId: Int, displayName: String?, avatarUrl: String?, description: String) {
        require(description.length <= 2000) { "Profile description is too long" }
        displayName?.let { require(it.isNotBlank() && it.length <= 128) { "Display name is invalid" } }
        avatarUrl?.let { require(it.length <= 2048) { "Avatar URL is too long" } }
        transaction(DBManager.db) {
            if (displayName != null) {
                UserTable.update({ UserTable.id eq userId }) {
                    it[UserTable.displayName] = displayName
                }
            }
            if (avatarUrl != null) {
                UserTable.update({ UserTable.id eq userId }) {
                    it[UserTable.avatarUrl] = avatarUrl.ifBlank { null }
                }
            }
            UserProfileTable.upsert(UserProfileTable.user) {
                it[user] = userId
                it[UserProfileTable.description] = description
            }
        }
    }

    fun addFavorite(userId: Int, mangaId: Int) {
        CategoryAccessService.requireReadableManga(userId, listOf(mangaId))
        transaction(DBManager.db) {
            UserFavoriteTable.upsert(UserFavoriteTable.user, UserFavoriteTable.manga) {
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

    fun favoriteMangaIds(userId: Int): List<Int> = favoriteMangaIdsForViewer(userId, userId)

    fun favoriteMangaIdsForViewer(viewerId: Int, profileUserId: Int): List<Int> = transaction(DBManager.db) {
        val visible = CategoryAccessService.readableMangaIds(viewerId)
        if (visible.isEmpty()) return@transaction emptyList()
        UserFavoriteTable
            .select(UserFavoriteTable.manga)
            .where { (UserFavoriteTable.user eq profileUserId) and (UserFavoriteTable.manga inList visible) }
            .map { it[UserFavoriteTable.manga].value }
    }

    fun publicProfile(viewerId: Int, profileUserId: Int): PublicProfile? = transaction(DBManager.db) {
        UserTable
            .selectAll()
            .where { UserTable.id eq profileUserId and (UserTable.enabled eq true) }
            .firstOrNull()
            ?.let { row ->
                PublicProfile(
                    id = row[UserTable.id].value,
                    username = row[UserTable.username],
                    displayName = row[UserTable.displayName],
                    avatarUrl = row[UserTable.avatarUrl],
                    description = description(profileUserId),
                    favoriteMangaIds = favoriteMangaIdsForViewer(viewerId, profileUserId),
                )
            }
    }

    data class PublicProfile(
        val id: Int,
        val username: String,
        val displayName: String,
        val avatarUrl: String?,
        val description: String,
        val favoriteMangaIds: List<Int>,
    )

    fun directory(): List<PublicProfile> = transaction(DBManager.db) {
        UserTable
            .selectAll()
            .where { UserTable.enabled eq true }
            .orderBy(UserTable.username)
            .map { row ->
                PublicProfile(
                    id = row[UserTable.id].value,
                    username = row[UserTable.username],
                    displayName = row[UserTable.displayName],
                    avatarUrl = row[UserTable.avatarUrl],
                    description = description(row[UserTable.id].value),
                    favoriteMangaIds = emptyList(),
                )
            }
    }
}
