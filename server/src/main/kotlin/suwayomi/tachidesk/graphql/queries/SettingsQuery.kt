package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.user.PermissionNodes

class SettingsQuery {
    @RequirePermission(PermissionNodes.SETTINGS_EDIT)
    fun settings(): SettingsType = SettingsType()
}
