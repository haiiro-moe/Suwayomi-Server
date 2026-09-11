package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserCategoryAccessTable
import suwayomi.tachidesk.server.user.model.UserTable

object CategoryAccessService {
    data class UserCategoryAccess(
        val userId: Int,
        val categoryId: Int,
        val canRead: Boolean,
        val canEdit: Boolean,
    )

    fun accessForUser(userId: Int): List<UserCategoryAccess> = transaction(DBManager.db) {
        UserCategoryAccessTable
            .selectAll()
            .where { UserCategoryAccessTable.user eq userId }
            .map {
                UserCategoryAccess(
                    userId = it[UserCategoryAccessTable.user].value,
                    categoryId = it[UserCategoryAccessTable.category].value,
                    canRead = it[UserCategoryAccessTable.canRead],
                    canEdit = it[UserCategoryAccessTable.canEdit],
                )
            }
    }

    fun setAccess(userId: Int, categoryId: Int, read: Boolean, edit: Boolean) {
        transaction(DBManager.db) {
            val normalizedRead = read || edit
            val existing = UserCategoryAccessTable
                .selectAll()
                .where {
                    (UserCategoryAccessTable.user eq userId) and
                        (UserCategoryAccessTable.category eq categoryId)
                }
                .firstOrNull()
            if (!normalizedRead && !edit) {
                UserCategoryAccessTable.deleteWhere {
                    (UserCategoryAccessTable.user eq userId) and
                        (UserCategoryAccessTable.category eq categoryId)
                }
            } else if (existing == null) {
                UserCategoryAccessTable.insert {
                    it[user] = userId
                    it[category] = categoryId
                    it[canRead] = normalizedRead
                    it[canEdit] = edit
                }
            } else {
                UserCategoryAccessTable.update({ UserCategoryAccessTable.id eq existing[UserCategoryAccessTable.id] }) {
                    it[canRead] = normalizedRead
                    it[canEdit] = edit
                }
            }
        }
    }

    fun accessFor(userId: Int, categoryId: Int): CategoryAccess =
        transaction(DBManager.db) {
            if (isOwner(userId)) return@transaction CategoryAccess(read = true, edit = true)
            UserCategoryAccessTable
                .selectAll()
                .where {
                    (UserCategoryAccessTable.user eq userId) and
                        (UserCategoryAccessTable.category eq categoryId)
                }
                .firstOrNull()
                ?.let { CategoryAccess(it[UserCategoryAccessTable.canRead], it[UserCategoryAccessTable.canEdit]) }
                ?: CategoryAccess(read = false, edit = false)
        }

    fun readableCategoryIds(userId: Int): List<Int> =
        transaction(DBManager.db) {
            if (isOwner(userId)) {
                CategoryTable.select(CategoryTable.id).map { it[CategoryTable.id].value }
            } else {
                UserCategoryAccessTable
                    .select(UserCategoryAccessTable.category)
                    .where {
                        (UserCategoryAccessTable.user eq userId) and
                            ((UserCategoryAccessTable.canRead eq true) or (UserCategoryAccessTable.canEdit eq true))
                    }
                    .map { it[UserCategoryAccessTable.category].value }
            }
        }

    fun readableMangaIds(userId: Int): List<Int> =
        transaction(DBManager.db) {
            if (isOwner(userId)) {
                return@transaction MangaTable.select(MangaTable.id).map { it[MangaTable.id].value }
            }

            val readableCategories = readableCategoryIds(userId).toSet()
            val categorizedMangaIds =
                CategoryMangaTable
                    .select(CategoryMangaTable.manga)
                    .withDistinct()
                    .map { it[CategoryMangaTable.manga].value }
                    .toSet()
            val readableCategorizedMangaIds =
                CategoryMangaTable
                    .select(CategoryMangaTable.manga)
                    .where { CategoryMangaTable.category inList readableCategories }
                    .withDistinct()
                    .map { it[CategoryMangaTable.manga].value }
                    .toSet()

            MangaTable
                .select(MangaTable.id, MangaTable.inLibrary)
                .map { row ->
                    val mangaId = row[MangaTable.id].value
                    val inLibrary = row[MangaTable.inLibrary]
                    mangaId to (!inLibrary || mangaId !in categorizedMangaIds || mangaId in readableCategorizedMangaIds)
                }.filter { it.second }
                .map { it.first }
        }

    fun requireReadableManga(userId: Int, mangaIds: Collection<Int>) {
        val visible = readableMangaIds(userId).toSet()
        require(mangaIds.all { it in visible }) { "Manga is not visible to this user" }
    }

    fun requireEditableCategories(userId: Int, categoryIds: Collection<Int>) {
        require(categoryIds.all { accessFor(userId, it).canEdit }) {
            "Category is not editable by this user"
        }
    }

    private fun isOwner(userId: Int): Boolean =
        UserTable
            .innerJoin(RoleTable)
            .selectAll()
            .where { UserTable.id eq userId }
            .firstOrNull()
            ?.get(RoleTable.name) == "owner"
}
