package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0074_MangaRequestDecision : SQLMigration() {
    // language=sql
    override val sql: String =
        """
        ALTER TABLE manga_requests ADD COLUMN decided_by_id INT NULL;
        ALTER TABLE manga_requests ADD COLUMN decided_at BIGINT NULL;
        """.trimIndent()
}
