package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.PermissionTable
import suwayomi.tachidesk.server.user.model.RolePermissionTable
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserTable

object UserAdminService {
    data class Role(
        val id: Int,
        val name: String,
        val description: String,
        val permissions: Set<String>,
    )

    fun permissionCatalog(): List<String> = PermissionNodes.catalog.sorted()

    fun roles(): List<Role> = transaction(DBManager.db) {
        RoleTable.selectAll().orderBy(RoleTable.name).map(::toRole)
    }

    fun createRole(name: String, description: String, permissions: Set<String>): Int = transaction(DBManager.db) {
        validateRole(name, permissions)
        val roleId = RoleTable.insertAndGetId {
            it[RoleTable.name] = name.trim()
            it[RoleTable.description] = description.trim()
        }
        replacePermissions(roleId.value, permissions)
        roleId.value
    }

    fun updateRole(roleId: Int, name: String, description: String, permissions: Set<String>) {
        transaction(DBManager.db) {
            val role = RoleTable.selectAll().where { RoleTable.id eq roleId }.firstOrNull()
                ?: error("Role does not exist")
            require(role[RoleTable.name] != "owner") { "The owner role cannot be changed" }
            validateRole(name, permissions)
            RoleTable.update({ RoleTable.id eq roleId }) {
                it[RoleTable.name] = name.trim()
                it[RoleTable.description] = description.trim()
            }
            replacePermissions(roleId, permissions)
        }
    }

    fun deleteRole(roleId: Int) {
        transaction(DBManager.db) {
            val role = RoleTable.selectAll().where { RoleTable.id eq roleId }.firstOrNull()
                ?: error("Role does not exist")
            require(role[RoleTable.name] != "owner") { "The owner role cannot be deleted" }
            require(UserTable.selectAll().where { UserTable.role eq roleId }.none()) { "Role is assigned to a user" }
            RoleTable.deleteWhere { RoleTable.id eq roleId }
        }
    }

    fun createUser(username: String, password: String, displayName: String, roleId: Int): Int = transaction(DBManager.db) {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotEmpty()) { "Username must not be empty" }
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(RoleTable.selectAll().where { RoleTable.id eq roleId }.any()) { "Role does not exist" }
        require(UserTable.selectAll().where { UserTable.username eq normalizedUsername }.none()) { "Username is already in use" }
        UserTable.insertAndGetId {
            it[UserTable.username] = normalizedUsername
            it[UserTable.passwordHash] = UserService.hashPasswordForAdmin(password)
            it[UserTable.displayName] = displayName.trim().ifEmpty { normalizedUsername }
            it[UserTable.role] = roleId
        }.value
    }

    fun updateUser(userId: Int, displayName: String?, avatarUrl: String?, password: String?, enabled: Boolean?, roleId: Int?) {
        transaction(DBManager.db) {
            val user = UserTable.selectAll().where { UserTable.id eq userId }.firstOrNull()
                ?: error("User does not exist")
            val currentRoleName = user[UserTable.role]?.let { assignedRoleId ->
                RoleTable.selectAll().where { RoleTable.id eq assignedRoleId }.firstOrNull()?.get(RoleTable.name)
            }
            if (roleId != null) {
                require(RoleTable.selectAll().where { RoleTable.id eq roleId }.any()) { "Role does not exist" }
                require(currentRoleName != "owner" || roleId == user[UserTable.role]?.value) { "The owner role cannot be changed" }
            }
            require(currentRoleName != "owner" || enabled != false) { "The owner user cannot be disabled" }
            password?.let { require(it.isNotEmpty()) { "Password must not be empty" } }
            UserTable.update({ UserTable.id eq userId }) {
                displayName?.let { value -> it[UserTable.displayName] = value.trim().ifEmpty { user[UserTable.username] } }
                avatarUrl?.let { value -> it[UserTable.avatarUrl] = value.trim().ifEmpty { null } }
                password?.let { value -> it[UserTable.passwordHash] = UserService.hashPasswordForAdmin(value) }
                enabled?.let { value -> it[UserTable.enabled] = value }
                roleId?.let { value -> it[UserTable.role] = value }
            }
        }
    }

    fun deleteUser(userId: Int) {
        transaction(DBManager.db) {
            val user = UserTable.selectAll().where { UserTable.id eq userId }.firstOrNull()
                ?: error("User does not exist")
            val roleName = user[UserTable.role]?.let { roleId ->
                RoleTable.selectAll().where { RoleTable.id eq roleId }.firstOrNull()?.get(RoleTable.name)
            }
            require(roleName != "owner") { "The owner user cannot be deleted" }
            UserTable.deleteWhere { UserTable.id eq userId }
        }
    }

    private fun validateRole(name: String, permissions: Set<String>) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Role name must not be empty" }
        require(normalizedName != "owner") { "The owner role name is reserved" }
        require(permissions.all { it in PermissionNodes.catalog }) { "Unknown permission node" }
    }

    private fun replacePermissions(roleId: Int, permissions: Set<String>) {
        RolePermissionTable.deleteWhere { RolePermissionTable.role eq roleId }
        permissions.forEach { node ->
            val permissionId = PermissionTable
                .selectAll()
                .where { PermissionTable.node eq node }
                .firstOrNull()
                ?.get(PermissionTable.id)?.value
                ?: PermissionTable.insertAndGetId { it[PermissionTable.node] = node }.value
            RolePermissionTable.insertAndGetId {
                it[RolePermissionTable.role] = roleId
                it[RolePermissionTable.permission] = permissionId
            }
        }
    }

    private fun toRole(row: org.jetbrains.exposed.v1.core.ResultRow): Role {
        val roleId = row[RoleTable.id].value
        val permissions = RolePermissionTable
            .innerJoin(PermissionTable)
            .selectAll()
            .where { RolePermissionTable.role eq roleId }
            .map { it[PermissionTable.node] }
            .toSet()
        return Role(roleId, row[RoleTable.name], row[RoleTable.description], permissions)
    }
}
