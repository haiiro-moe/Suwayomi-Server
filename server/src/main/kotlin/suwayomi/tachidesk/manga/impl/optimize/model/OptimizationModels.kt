package suwayomi.tachidesk.manga.impl.optimize.model

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

enum class OptimizationState {
    Queued,
    Optimizing,
    Finished,
    Error,
}

class OptimizationQueueItem(
    val chapterId: Int,
    val mangaId: Int,
    var state: OptimizationState = OptimizationState.Queued,
    var progress: Float = 0f,
    var tries: Int = 0,
    /** size of the downloaded files before optimizing, known once the chapter got optimized */
    var originalSize: Long? = null,
    /** size of the downloaded files after optimizing, known once the chapter got optimized */
    var optimizedSize: Long? = null,
) {
    override fun toString(): String = "$mangaId - $chapterId | state= $state, tries= $tries, progress= $progress"
}

enum class OptimizationUpdateType {
    QUEUED,
    DEQUEUED,
    PROGRESS,
    FINISHED,
    ERROR,
    POSITION,
}

data class OptimizationUpdate(
    val type: OptimizationUpdateType,
    val item: OptimizationQueueItem,
    val position: Int,
)

enum class OptimizerStatus {
    Stopped,
    Started,
}

data class OptimizationStatus(
    val status: OptimizerStatus,
    val queue: List<OptimizationQueueItem>,
)

data class OptimizationUpdates(
    val status: OptimizerStatus,
    val updates: List<OptimizationUpdate>,
    val initial: List<OptimizationQueueItem>?,
)
