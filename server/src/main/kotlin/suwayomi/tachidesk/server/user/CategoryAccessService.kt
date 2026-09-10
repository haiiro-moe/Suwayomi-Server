package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserCategoryAccessTable
import suwayomi.tachidesk.server.user.model.UserTable

object CategoryAccessService {
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

    fun readableMangaIds(userId: Int): List<Int> {
        val categoryIds = readableCategoryIds(userId)
        if (categoryIds.isEmpty()) return emptyList()
        return transaction(DBManager.db) {
            CategoryMangaTable
                .select(CategoryMangaTable.manga)
                .where { CategoryMangaTable.category inList categoryIds }
                .withDistinct()
                .map { it[CategoryMangaTable.manga].value }
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
