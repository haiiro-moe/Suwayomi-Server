package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserSettingTable

object UserSettingService {
    private val allowedKeys = setOf(
        "locale",
        "appTheme",
        "themeMode",
        "shouldUsePureBlackMode",
        "mangaThumbnailBackdrop",
        "mangaDynamicColorSchemes",
        "mangaGridItemWidth",
        "hideHistory",
        "updateProgressAfterReading",
        "updateProgressManualMarkRead",
    )

    fun read(userId: Int): Map<String, String> = transaction(DBManager.db) {
        UserSettingTable.selectAll()
            .where { UserSettingTable.user eq userId }
            .associate { it[UserSettingTable.key] to it[UserSettingTable.value] }
    }

    fun set(userId: Int, values: Map<String, String>) {
        require(values.keys.all { it in allowedKeys }) { "Unsupported user setting" }
        require(values.values.all { it.length <= 4096 }) { "User setting value is too long" }
        transaction(DBManager.db) {
            values.forEach { (key, value) ->
                val existing = UserSettingTable.selectAll().where {
                    (UserSettingTable.user eq userId) and (UserSettingTable.key eq key)
                }.any()
                if (existing) {
                    UserSettingTable.update({
                        (UserSettingTable.user eq userId) and (UserSettingTable.key eq key)
                    }) { it[UserSettingTable.value] = value }
                } else {
                    UserSettingTable.insertIgnore {
                        it[user] = userId
                        it[UserSettingTable.key] = key
                        it[UserSettingTable.value] = value
                    }
                }
            }
        }
    }

    fun reset(userId: Int) {
        transaction(DBManager.db) {
            UserSettingTable.deleteWhere { UserSettingTable.user eq userId }
        }
    }
}
