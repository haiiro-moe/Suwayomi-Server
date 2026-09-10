package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UserSettingService
import suwayomi.tachidesk.server.user.requireUser

class UserSettingsQuery {
    data class UserSetting(val key: String, val value: String)

    @RequireAuth
    fun userSettings(dataFetchingEnvironment: DataFetchingEnvironment): List<UserSetting> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return UserSettingService.read(userId).entries
            .sortedBy { it.key }
            .map { UserSetting(it.key, it.value) }
    }
}
