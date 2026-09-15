package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.UserCategoryAccessTable

@Suppress("ClassName", "unused")
class M0067_UserCategoryAccess : AddTableMigration() {
	override val tables: Array<Table> = arrayOf(UserCategoryAccessTable)
}
