package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.UserAdminService

class UserRoleAdminMutation {
    data class CreateUserInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
        val displayName: String,
        val roleId: Int,
    )

    data class UserAdminPayload(val clientMutationId: String?, val id: Int)

    @RequirePermission("admin.users.manage")
    fun createUser(input: CreateUserInput): UserAdminPayload =
        UserAdminPayload(input.clientMutationId, UserAdminService.createUser(input.username, input.password, input.displayName, input.roleId))

    data class DeleteUserInput(val clientMutationId: String? = null, val userId: Int)

    data class OperationPayload(val clientMutationId: String?, val success: Boolean)

    @RequirePermission("admin.users.manage")
    fun deleteUser(input: DeleteUserInput): OperationPayload {
        UserAdminService.deleteUser(input.userId)
        return OperationPayload(input.clientMutationId, true)
    }

    data class RoleInput(
        val clientMutationId: String? = null,
        val name: String,
        val description: String = "",
        val permissions: List<String> = emptyList(),
    )

    @RequirePermission("admin.roles.manage")
    fun createRole(input: RoleInput): UserAdminPayload =
        UserAdminPayload(input.clientMutationId, UserAdminService.createRole(input.name, input.description, input.permissions.toSet()))

    data class UpdateRoleInput(
        val clientMutationId: String? = null,
        val roleId: Int,
        val name: String,
        val description: String = "",
        val permissions: List<String> = emptyList(),
    )

    @RequirePermission("admin.roles.manage")
    fun updateRole(input: UpdateRoleInput): OperationPayload {
        UserAdminService.updateRole(input.roleId, input.name, input.description, input.permissions.toSet())
        return OperationPayload(input.clientMutationId, true)
    }

    data class DeleteRoleInput(val clientMutationId: String? = null, val roleId: Int)

    @RequirePermission("admin.roles.manage")
    fun deleteRole(input: DeleteRoleInput): OperationPayload {
        UserAdminService.deleteRole(input.roleId)
        return OperationPayload(input.clientMutationId, true)
    }
}
