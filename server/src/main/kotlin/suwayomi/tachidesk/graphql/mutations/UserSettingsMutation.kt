package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UserSettingService
import suwayomi.tachidesk.server.user.requireUser

class UserSettingsMutation {
    data class UserSettingInput(val key: String, val value: String)

    data class SetUserSettingsInput(
        val clientMutationId: String? = null,
        val settings: List<UserSettingInput>,
    )

    data class SetUserSettingsPayload(
        val clientMutationId: String?,
        val updated: Boolean,
    )

    @RequireAuth
    fun setUserSettings(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: SetUserSettingsInput,
    ): SetUserSettingsPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserSettingService.set(userId, input.settings.associate { it.key to it.value })
        return SetUserSettingsPayload(input.clientMutationId, true)
    }

    data class ResetUserSettingsInput(val clientMutationId: String? = null)

    @RequireAuth
    fun resetUserSettings(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ResetUserSettingsInput,
    ): SetUserSettingsPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserSettingService.reset(userId)
        return SetUserSettingsPayload(input.clientMutationId, true)
    }
}
