package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.UserFavoriteTable
import suwayomi.tachidesk.server.user.model.UserProfileTable

@Suppress("ClassName", "unused")
class M0068_UserProfilesFavorites : AddTableMigration() {
    override val tables: Array<Table> = arrayOf(UserProfileTable, UserFavoriteTable)
}
