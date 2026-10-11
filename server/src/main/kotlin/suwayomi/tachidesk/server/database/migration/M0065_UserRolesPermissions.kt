package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.PermissionTable
import suwayomi.tachidesk.server.user.model.RolePermissionTable
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserTable

@Suppress("ClassName", "unused")
class M0065_UserRolesPermissions : AddTableMigration() {
    override val tables: Array<Table> =
        arrayOf(
            RoleTable,
            PermissionTable,
            UserTable,
            RolePermissionTable,
        )
}
