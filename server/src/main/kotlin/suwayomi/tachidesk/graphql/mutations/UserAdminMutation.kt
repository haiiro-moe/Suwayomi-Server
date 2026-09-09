package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.UserService
import suwayomi.tachidesk.server.user.model.UserTable

class UserAdminMutation {
	data class UpdateUserInput(
		val id: Int,
		val displayName: String? = null,
		val avatarUrl: String? = null,
		val password: String? = null,
		val enabled: Boolean? = null,
	)

	data class UpdateUserPayload(
		val clientMutationId: String?,
		val updated: Boolean,
	)

	@RequirePermission("users.manage")
	fun updateUser(input: UpdateUserInput): UpdateUserPayload {
		transaction(DBManager.db) {
			UserTable.update({ UserTable.id eq input.id }) {
				input.displayName?.let { value -> it[UserTable.displayName] = value }
				input.avatarUrl?.let { value -> it[UserTable.avatarUrl] = value }
				input.password?.let { value -> it[UserTable.passwordHash] = UserService.hashPasswordForAdmin(value) }
				input.enabled?.let { value -> it[UserTable.enabled] = value }
			}
		}
		return UpdateUserPayload(null, true)
	}
}
