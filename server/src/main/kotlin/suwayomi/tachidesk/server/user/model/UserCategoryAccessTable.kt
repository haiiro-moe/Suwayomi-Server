package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.CategoryTable

object UserCategoryAccessTable : IntIdTable("user_category_access") {
	val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
	val category = reference("category_id", CategoryTable, onDelete = ReferenceOption.CASCADE)
	val canRead = bool("can_read").default(false)
	val canEdit = bool("can_edit").default(false)

	init {
		uniqueIndex(user, category)
		index(false, user, canRead)
	}
}
