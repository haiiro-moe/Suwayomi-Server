package suwayomi.tachidesk.server.database.migration.snapshot

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.server.user.model.UserTable

/**
 * Frozen snapshot of UserProfileTable as it looked at migration M0068.
 *
 * Migration migrations must never reference the live table objects: `AddTableMigration`
 * creates tables from whatever columns the object currently declares, so a column added
 * to the live object later would already exist on fresh databases and break the follow-up
 * `AddColumnMigration` with a duplicate-column error.
 *
 * This file MUST stay outside the `database.migration` package: the migration loader
 * reflects over every class in that package and Kotlin `object`s have private
 * constructors, which makes the loader fail with IllegalAccessException.
 */
object UserProfileTableV1 : IntIdTable("user_profiles") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val description = varchar("description", 2000).default("")
    val bannerUrl = varchar("banner_url", 2048).nullable()
}
