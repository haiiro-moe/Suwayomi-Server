package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.RoleCategoryAccessTable
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserCategoryAccessTable
import suwayomi.tachidesk.server.user.model.UserTable

/**
 * Category access is granted per role, optionally extended per user.
 *
 * The effective access of a user is the union of the grants of their role and their own grants, so
 * per-user entries can only add access on top of the role, never take it away.
 */
object CategoryAccessService {
    data class UserCategoryAccess(
        val userId: Int,
        val categoryId: Int,
        val canRead: Boolean,
        val canEdit: Boolean,
    )

    data class RoleCategoryAccess(
        val roleId: Int,
        val categoryId: Int,
        val canRead: Boolean,
        val canEdit: Boolean,
    )

    fun accessForUser(userId: Int): List<UserCategoryAccess> =
        transaction(DBManager.db) {
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

    fun accessForRole(roleId: Int): List<RoleCategoryAccess> =
        transaction(DBManager.db) {
            RoleCategoryAccessTable
                .selectAll()
                .where { RoleCategoryAccessTable.role eq roleId }
                .map {
                    RoleCategoryAccess(
                        roleId = it[RoleCategoryAccessTable.role].value,
                        categoryId = it[RoleCategoryAccessTable.category].value,
                        canRead = it[RoleCategoryAccessTable.canRead],
                        canEdit = it[RoleCategoryAccessTable.canEdit],
                    )
                }
        }

    fun setAccess(
        userId: Int,
        categoryId: Int,
        read: Boolean,
        edit: Boolean,
    ) {
        transaction(DBManager.db) {
            if (!read && !edit) {
                UserCategoryAccessTable.deleteWhere {
                    (UserCategoryAccessTable.user eq userId) and (UserCategoryAccessTable.category eq categoryId)
                }
                return@transaction
            }

            UserCategoryAccessTable.upsert(UserCategoryAccessTable.user, UserCategoryAccessTable.category) {
                it[user] = userId
                it[category] = categoryId
                it[canRead] = true
                it[canEdit] = edit
            }
        }
    }

    fun setRoleAccess(
        roleId: Int,
        categoryIds: Collection<Int>,
        read: Boolean,
        edit: Boolean,
    ) {
        transaction(DBManager.db) {
            if (!read && !edit) {
                RoleCategoryAccessTable.deleteWhere {
                    (RoleCategoryAccessTable.role eq roleId) and (RoleCategoryAccessTable.category inList categoryIds)
                }
                return@transaction
            }

            categoryIds.forEach { categoryId ->
                RoleCategoryAccessTable.upsert(RoleCategoryAccessTable.role, RoleCategoryAccessTable.category) {
                    it[role] = roleId
                    it[category] = categoryId
                    it[canRead] = true
                    it[canEdit] = edit
                }
            }
        }
    }

    fun accessFor(
        userId: Int,
        categoryId: Int,
    ): CategoryAccess =
        transaction(DBManager.db) {
            if (isOwner(userId)) return@transaction CategoryAccess(read = true, edit = true)

            val userAccess =
                UserCategoryAccessTable
                    .selectAll()
                    .where { (UserCategoryAccessTable.user eq userId) and (UserCategoryAccessTable.category eq categoryId) }
                    .firstOrNull()
            val roleAccess =
                roleOf(userId)?.let { roleId ->
                    RoleCategoryAccessTable
                        .selectAll()
                        .where { (RoleCategoryAccessTable.role eq roleId) and (RoleCategoryAccessTable.category eq categoryId) }
                        .firstOrNull()
                }

            CategoryAccess(
                read =
                    userAccess?.get(UserCategoryAccessTable.canRead) == true ||
                        roleAccess?.get(RoleCategoryAccessTable.canRead) == true,
                edit =
                    userAccess?.get(UserCategoryAccessTable.canEdit) == true ||
                        roleAccess?.get(RoleCategoryAccessTable.canEdit) == true,
            )
        }

    fun readableCategoryIds(userId: Int): List<Int> =
        transaction(DBManager.db) {
            if (isOwner(userId)) {
                return@transaction CategoryTable.select(CategoryTable.id).map { it[CategoryTable.id].value }
            }

            val userCategories =
                UserCategoryAccessTable
                    .select(UserCategoryAccessTable.category)
                    .where {
                        (UserCategoryAccessTable.user eq userId) and
                            ((UserCategoryAccessTable.canRead eq true) or (UserCategoryAccessTable.canEdit eq true))
                    }.map { it[UserCategoryAccessTable.category].value }
            val roleCategories =
                roleOf(userId)
                    ?.let { roleId ->
                        RoleCategoryAccessTable
                            .select(RoleCategoryAccessTable.category)
                            .where {
                                (RoleCategoryAccessTable.role eq roleId) and
                                    ((RoleCategoryAccessTable.canRead eq true) or (RoleCategoryAccessTable.canEdit eq true))
                            }.map { it[RoleCategoryAccessTable.category].value }
                    }.orEmpty()

            (userCategories + roleCategories).distinct()
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

    fun requireReadableManga(
        userId: Int,
        mangaIds: Collection<Int>,
    ) {
        val visible = readableMangaIds(userId).toSet()
        require(mangaIds.all { it in visible }) { "Manga is not visible to this user" }
    }

    fun requireEditableCategories(
        userId: Int,
        categoryIds: Collection<Int>,
    ) {
        require(categoryIds.all { accessFor(userId, it).canEdit }) {
            "Category is not editable by this user"
        }
    }

    private fun roleOf(userId: Int): Int? =
        UserTable
            .select(UserTable.role)
            .where { UserTable.id eq userId }
            .firstOrNull()
            ?.get(UserTable.role)
            ?.value

    private fun isOwner(userId: Int): Boolean =
        UserTable
            .innerJoin(RoleTable)
            .selectAll()
            .where { UserTable.id eq userId }
            .firstOrNull()
            ?.get(RoleTable.name) == "owner"
}
