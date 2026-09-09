package suwayomi.tachidesk.graphql.queries

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserTable

class UserQuery {
	data class UserProfile(
		val id: Int,
		val username: String,
		val displayName: String,
		val avatarUrl: String?,
		val role: String,
	)

	@RequireAuth
	fun currentUser(userId: Int): UserProfile? =
		transaction(DBManager.db) {
			UserTable
				.leftJoin(RoleTable)
				.selectAll()
				.where { UserTable.id eq userId }
				.firstOrNull()
				?.let { row ->
					UserProfile(
						id = row[UserTable.id].value,
						username = row[UserTable.username],
						displayName = row[UserTable.displayName],
						avatarUrl = row[UserTable.avatarUrl],
						role = row.getOrNull(RoleTable.name) ?: "unknown",
					)
				}
		}

	@RequirePermission("users.manage")
	fun users(): List<UserProfile> =
		transaction(DBManager.db) {
			UserTable
				.leftJoin(RoleTable)
				.selectAll()
				.orderBy(UserTable.username)
				.map { row ->
					UserProfile(
						id = row[UserTable.id].value,
						username = row[UserTable.username],
						displayName = row[UserTable.displayName],
						avatarUrl = row[UserTable.avatarUrl],
						role = row.getOrNull(RoleTable.name) ?: "unknown",
					)
				}
		}
}
