package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.server.user.model.RoleTable

object UserTable : IntIdTable("users") {
    val username = varchar("username", 128).uniqueIndex()
    val passwordHash = varchar("password_hash", 512)
    val displayName = varchar("display_name", 128)
    val avatarUrl = varchar("avatar_url", 2048).nullable()
    val role = reference("role_id", RoleTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val enabled = bool("enabled").default(true)
}

object RoleTable : IntIdTable("roles") {
    val name = varchar("name", 128).uniqueIndex()
    val description = varchar("description", 512).default("")
}

object PermissionTable : IntIdTable("permissions") {
    val node = varchar("node", 256).uniqueIndex()
}

object RolePermissionTable : IntIdTable("role_permissions") {
    val role = reference("role_id", RoleTable, onDelete = ReferenceOption.CASCADE)
    val permission = reference("permission_id", PermissionTable, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(role, permission)
    }
}
