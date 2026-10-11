package suwayomi.tachidesk.server.database.migration.snapshot

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.user.model.UserTable

/**
 * Frozen snapshot of MangaRequestTable as it looked at migration M0073.
 *
 * See UserProfileTableV1 for why migrations must not reference live table objects.
 */
object MangaRequestTableV1 : IntIdTable("manga_requests") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    val status = varchar("status", 32).default("PENDING")
    // decided_by_id and decided_at intentionally omitted: they are added by M0074_MangaRequestDecision.

    init {
        uniqueIndex(user, manga)
    }
}
