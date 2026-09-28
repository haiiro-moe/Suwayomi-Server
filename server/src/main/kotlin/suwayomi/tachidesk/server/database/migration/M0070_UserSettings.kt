package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.UserSettingTable

@Suppress("ClassName", "unused")
class M0070_UserSettings : AddTableMigration() {
    override val tables: Array<Table> = arrayOf(UserSettingTable)
}
