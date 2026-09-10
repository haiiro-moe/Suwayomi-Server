package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.server.user.CategoryAccessService

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
    @RequirePermission("users.manage")
    fun setCategoryAccess(input: SetCategoryAccessInput): SetCategoryAccessPayload {
        CategoryAccessService.setAccess(input.userId, input.categoryId, input.canRead, input.canEdit)
        return SetCategoryAccessPayload(input.clientMutationId, true)
    }
}
