package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserSettingTable
import suwayomi.tachidesk.server.user.model.UserTable

private const val MAX_SETTINGS_PER_USER = 100

private fun requireExistingUser(userId: Int) {
    require(transaction(DBManager.db) {
        UserTable.selectAll().where { UserTable.id eq userId }.any()
    }) { "User does not exist" }
}

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

    fun read(userId: Int): Map<String, String> {
        requireExistingUser(userId)
        return transaction(DBManager.db) {
            UserSettingTable.selectAll()
                .where { UserSettingTable.user eq userId }
                .associate { it[UserSettingTable.key] to it[UserSettingTable.value] }
        }
    }

    fun set(userId: Int, values: Map<String, String>) {
        requireExistingUser(userId)
        require(values.size <= MAX_SETTINGS_PER_USER) { "Too many user settings" }
        require(values.keys.all { it in allowedKeys }) { "Unsupported user setting" }
        require(values.values.all { it.length <= 4096 }) { "User setting value is too long" }
        transaction(DBManager.db) {
            values.forEach { (key, value) ->
                UserSettingTable.upsert(UserSettingTable.user, UserSettingTable.key) {
                    it[UserSettingTable.user] = userId
                    it[UserSettingTable.key] = key
                    it[UserSettingTable.value] = value
                }
            }
        }
    }

    fun reset(userId: Int) {
        requireExistingUser(userId)
        transaction(DBManager.db) {
            UserSettingTable.deleteWhere { UserSettingTable.user eq userId }
        }
    }
}
