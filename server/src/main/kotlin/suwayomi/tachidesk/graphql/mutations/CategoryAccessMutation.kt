package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.CategoryAccessService
import suwayomi.tachidesk.server.user.PermissionNodes

data class SetCategoryAccessInput(
    val clientMutationId: String? = null,
    val userId: Int,
    val categoryId: Int,
    val canRead: Boolean = false,
    val canEdit: Boolean = false,
)

data class SetCategoryAccessPayload(
    val clientMutationId: String?,
    val updated: Boolean,
)

class CategoryAccessMutation {
    @RequirePermission(PermissionNodes.ADMIN_USERS_MANAGE)
    fun setCategoryAccess(input: SetCategoryAccessInput): SetCategoryAccessPayload {
        CategoryAccessService.setAccess(input.userId, input.categoryId, input.canRead, input.canEdit)
        return SetCategoryAccessPayload(input.clientMutationId, true)
    }
}

@Suppress("unused")
data class CategoryAccessType(
    val userId: Int,
    val categoryId: Int,
    val canRead: Boolean,
    val canEdit: Boolean,
)

@Suppress("unused")
class CategoryAccessQuery {
    @RequirePermission(PermissionNodes.ADMIN_USERS_MANAGE)
    fun categoryAccess(userId: Int): List<CategoryAccessType> =
        CategoryAccessService.accessForUser(userId).map {
            CategoryAccessType(it.userId, it.categoryId, it.canRead, it.canEdit)
        }
}