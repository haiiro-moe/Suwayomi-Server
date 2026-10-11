package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.UserAdminService

class RoleQuery {
    data class RoleType(
        val id: Int,
        val name: String,
        val description: String,
        val permissions: List<String>,
    )

    @RequirePermission("admin.roles.manage")
    fun roles(): List<RoleType> = UserAdminService.roles().map {
        RoleType(it.id, it.name, it.description, it.permissions.sorted())
    }

    @RequirePermission("admin.roles.manage")
    fun permissionNodes(): List<String> = UserAdminService.permissionCatalog()
}
