package suwayomi.tachidesk.graphql.queries

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.UserMessageService
import suwayomi.tachidesk.server.user.UserProfileService
import suwayomi.tachidesk.server.user.UserService
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.model.UserTable
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.requireUser


class UserQuery {
    data class UserProfile(
        val id: Int,
        val username: String,
        val displayName: String,
        val avatarUrl: String?,
        val role: String,
        val description: String = "",
        val favoriteMangaIds: List<Int> = emptyList(),
        val permissions: Set<String> = emptySet(),
    )

    data class MessageType(
        val id: Int,
        val senderId: Int,
        val receiverId: Int,
        val parentId: Int?,
        val content: String,
        val createdAt: Long,
        val readAt: Long?,
    )

    @RequireAuth
    fun conversation(dataFetchingEnvironment: DataFetchingEnvironment, otherUserId: Int): List<MessageType> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return UserMessageService.conversation(userId, otherUserId).map {
            MessageType(it.id, it.senderId, it.receiverId, it.parentId, it.content, it.createdAt, it.readAt)
        }
    }

    @RequireAuth
    fun unreadMessageCount(dataFetchingEnvironment: DataFetchingEnvironment): Long {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return UserMessageService.unreadCount(userId)
    }

    @RequireAuth
    fun userDirectory(): List<UserProfile> =
        UserProfileService.directory().map { profile ->
            UserProfile(profile.id, profile.username, profile.displayName, profile.avatarUrl, "public", profile.description)
        }

    @RequireAuth
    fun profile(dataFetchingEnvironment: DataFetchingEnvironment, userId: Int): UserProfile? {
        val viewerId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return UserProfileService.publicProfile(viewerId, userId)?.let { publicProfile ->
            UserProfile(
                publicProfile.id,
                publicProfile.username,
                publicProfile.displayName,
                publicProfile.avatarUrl,
                "public",
                publicProfile.description,
                publicProfile.favoriteMangaIds,
            )
        }
    }

    @RequireAuth
    fun currentUserProfile(dataFetchingEnvironment: DataFetchingEnvironment): UserProfile? {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return currentUser(userId)?.copy(
            description = UserProfileService.description(userId),
            favoriteMangaIds = UserProfileService.favoriteMangaIds(userId),
            permissions = UserService.permissionsFor(userId),
        )
    }

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

	@RequirePermission(PermissionNodes.ADMIN_USERS_MANAGE)
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
