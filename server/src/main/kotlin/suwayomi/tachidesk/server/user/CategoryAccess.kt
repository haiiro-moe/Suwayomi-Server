package suwayomi.tachidesk.server.user

data class CategoryAccess(
	val read: Boolean,
	val edit: Boolean,
) {
	val canRead: Boolean get() = read || edit
	val canEdit: Boolean get() = edit
}
