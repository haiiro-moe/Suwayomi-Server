package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.user.UserAdminService

class UserAdminMutation {
	data class UpdateUserInput(
		val clientMutationId: String? = null,
		val id: Int,
		val displayName: String? = null,
		val avatarUrl: String? = null,
		val password: String? = null,
		val enabled: Boolean? = null,
		val roleId: Int? = null,
	)

	data class UpdateUserPayload(
		val clientMutationId: String?,
		val updated: Boolean,
	)

	@RequirePermission(PermissionNodes.ADMIN_USERS_MANAGE)
	fun updateUser(input: UpdateUserInput): UpdateUserPayload {
		UserAdminService.updateUser(input.id, input.displayName, input.avatarUrl, input.password, input.enabled, input.roleId)
		return UpdateUserPayload(input.clientMutationId, true)
	}
}
