package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.server.user.model.UserMangaNoteTable

@Suppress("ClassName", "unused")
class M0072_UserMangaNotes : AddTableMigration() {
    override val tables: Array<Table> = arrayOf(UserMangaNoteTable)
}
