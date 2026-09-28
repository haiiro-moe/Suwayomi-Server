package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.UserMessageTable

@Suppress("ClassName", "unused")
class M0069_UserMessages : AddTableMigration() {
    override val tables: Array<Table> = arrayOf(UserMessageTable)
}
