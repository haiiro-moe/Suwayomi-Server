/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.OptimizationUpdates
import suwayomi.tachidesk.manga.impl.optimize.OptimizationManager

class OptimizationSubscription {
    @RequireAuth
    fun optimizationStatusChanged(): Flow<OptimizationUpdates> = OptimizationManager.updates.map { OptimizationUpdates(it) }
}
