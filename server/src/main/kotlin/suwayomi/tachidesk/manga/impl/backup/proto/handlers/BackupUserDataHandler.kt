package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.backup.proto.models.*
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.*

/** Exports/restores all Suwairo user-owned records. Legacy backups have no userData field. */
object BackupUserDataHandler {
    fun backup(): BackupUserData = transaction(DBManager.db) {
        BackupUserData(
            roles = RoleTable.selectAll().map { BackupUserRole(it[RoleTable.id].value, it[RoleTable.name], it[RoleTable.description]) },
            permissions = PermissionTable.selectAll().map { BackupUserPermission(it[PermissionTable.id].value, it[PermissionTable.node]) },
            users = UserTable.selectAll().map { BackupUser(it[UserTable.id].value, it[UserTable.username], it[UserTable.passwordHash], it[UserTable.displayName], it[UserTable.avatarUrl], it[UserTable.role]?.value, it[UserTable.enabled]) },
            rolePermissions = RolePermissionTable.selectAll().map { BackupRolePermission(it[RolePermissionTable.role].value, it[RolePermissionTable.permission].value) },
            categoryAccess = UserCategoryAccessTable.selectAll().map { BackupUserCategoryAccess(it[UserCategoryAccessTable.user].value, it[UserCategoryAccessTable.category].value, it[UserCategoryAccessTable.canRead], it[UserCategoryAccessTable.canEdit]) },
            chapterStates = UserChapterStateTable.selectAll().map { BackupUserChapterState(it[UserChapterStateTable.user].value, it[UserChapterStateTable.chapter].value, it[UserChapterStateTable.isRead], it[UserChapterStateTable.isBookmarked], it[UserChapterStateTable.lastPageRead], it[UserChapterStateTable.lastReadAt]) },
            profiles = UserProfileTable.selectAll().map { BackupUserProfile(it[UserProfileTable.user].value, it[UserProfileTable.description]) },
            favorites = UserFavoriteTable.selectAll().map { BackupUserFavorite(it[UserFavoriteTable.user].value, it[UserFavoriteTable.manga].value) },
            messages = UserMessageTable.selectAll().map { BackupUserMessage(it[UserMessageTable.id].value, it[UserMessageTable.sender].value, it[UserMessageTable.receiver].value, it[UserMessageTable.parent]?.value, it[UserMessageTable.content], it[UserMessageTable.createdAt], it[UserMessageTable.readAt]) },
            settings = UserSettingTable.selectAll().map { BackupUserSetting(it[UserSettingTable.user].value, it[UserSettingTable.key], it[UserSettingTable.value]) },
        )
    }

    fun restore(data: BackupUserData?) {
        if (data == null) return
        transaction(DBManager.db) {
            data.roles.forEach { r -> RoleTable.insertIgnore { it[RoleTable.id] = r.id; it[name] = r.name; it[description] = r.description } }
            data.permissions.forEach { p -> PermissionTable.insertIgnore { it[PermissionTable.id] = p.id; it[node] = p.node } }
            data.users.forEach { u -> UserTable.insertIgnore { it[UserTable.id] = u.id; it[username] = u.username; it[passwordHash] = u.passwordHash; it[displayName] = u.displayName; it[avatarUrl] = u.avatarUrl; it[role] = u.roleId; it[enabled] = u.enabled } }
            data.rolePermissions.forEach { rp -> RolePermissionTable.insertIgnore { it[role] = rp.roleId; it[permission] = rp.permissionId } }
            data.categoryAccess.filter { categoryExists(it.categoryId) && userExists(it.userId) }.forEach { a -> UserCategoryAccessTable.insertIgnore { it[user] = a.userId; it[category] = a.categoryId; it[canRead] = a.canRead; it[canEdit] = a.canEdit } }
            data.chapterStates.filter { chapterExists(it.chapterId) && userExists(it.userId) }.forEach { s -> UserChapterStateTable.insertIgnore { it[user] = s.userId; it[chapter] = s.chapterId; it[isRead] = s.isRead; it[isBookmarked] = s.isBookmarked; it[lastPageRead] = s.lastPageRead; it[lastReadAt] = s.lastReadAt } }
            data.profiles.filter { userExists(it.userId) }.forEach { p -> UserProfileTable.insertIgnore { it[user] = p.userId; it[description] = p.description } }
            data.favorites.filter { userExists(it.userId) && mangaExists(it.mangaId) }.forEach { f -> UserFavoriteTable.insertIgnore { it[user] = f.userId; it[manga] = f.mangaId } }
            data.settings.filter { userExists(it.userId) }.forEach { s -> UserSettingTable.insertIgnore { it[user] = s.userId; it[key] = s.key; it[value] = s.value } }
            data.messages.filter { userExists(it.senderId) && userExists(it.receiverId) }.sortedBy { it.parentId != null }.forEach { m -> UserMessageTable.insertIgnore { it[UserMessageTable.id] = m.id; it[sender] = m.senderId; it[receiver] = m.receiverId; it[parent] = m.parentId; it[content] = m.content; it[createdAt] = m.createdAt; it[readAt] = m.readAt } }
        }
    }

    private fun userExists(id: Int) = UserTable.selectAll().where { UserTable.id eq id }.any()
    private fun categoryExists(id: Int) = CategoryTable.selectAll().where { CategoryTable.id eq id }.any()
    private fun chapterExists(id: Int) = ChapterTable.selectAll().where { ChapterTable.id eq id }.any()
    private fun mangaExists(id: Int) = MangaTable.selectAll().where { MangaTable.id eq id }.any()
}
