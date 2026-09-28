package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object UserSettingTable : IntIdTable("user_settings") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("setting_key", 128)
    val value = varchar("setting_value", 4096)

    init {
        uniqueIndex(user, key)
    }
}
