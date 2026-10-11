/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationQueueItem
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdateType
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizerStatus
import java.util.concurrent.CompletableFuture
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationState as InternalOptimizationState
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationStatus as InternalOptimizationStatus
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdate as InternalOptimizationUpdate
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdates as InternalOptimizationUpdates

enum class OptimizationState {
    QUEUED,
    OPTIMIZING,
    FINISHED,
    ERROR,
}

enum class OptimizerState {
    STARTED,
    STOPPED,
}

private fun OptimizerStatus.toOptimizerState(): OptimizerState =
    when (this) {
        OptimizerStatus.Started -> OptimizerState.STARTED
        OptimizerStatus.Stopped -> OptimizerState.STOPPED
    }

data class ChapterOptimizationReorder(
    val chapterId: Int,
    val to: Int,
)

data class OptimizationStatus(
    val state: OptimizerState,
    val queue: List<OptimizationType>,
) {
    constructor(status: InternalOptimizationStatus) : this(
        status.status.toOptimizerState(),
        status.queue.mapIndexed { index, item -> OptimizationType(item, index) },
    )
}

data class OptimizationUpdates(
    val state: OptimizerState,
    val updates: List<OptimizationUpdate>,
    @GraphQLDescription("The current optimization queue at the time of sending initial message. Is null for all following messages")
    val initial: List<OptimizationType>?,
) {
    constructor(updates: InternalOptimizationUpdates) : this(
        updates.status.toOptimizerState(),
        updates.updates.map { OptimizationUpdate(it) },
        updates.initial?.mapIndexed { index, item -> OptimizationType(item, index) },
    )
}

class OptimizationType(
    @get:GraphQLIgnore
    val chapterId: Int,
    @get:GraphQLIgnore
    val mangaId: Int,
    val state: OptimizationState,
    val progress: Float,
    val tries: Int,
    val position: Int,
    @GraphQLDescription("Size in bytes of the downloaded files before the optimization, only set once it finished")
    val originalSize: Long?,
    @GraphQLDescription("Size in bytes of the downloaded files after the optimization, only set once it finished")
    val optimizedSize: Long?,
) : Node {
    constructor(item: OptimizationQueueItem, position: Int) : this(
        item.chapterId,
        item.mangaId,
        when (item.state) {
            InternalOptimizationState.Queued -> OptimizationState.QUEUED
            InternalOptimizationState.Optimizing -> OptimizationState.OPTIMIZING
            InternalOptimizationState.Finished -> OptimizationState.FINISHED
            InternalOptimizationState.Error -> OptimizationState.ERROR
        },
        item.progress,
        item.tries,
        position,
        item.originalSize,
        item.optimizedSize,
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)

    fun chapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType> {
        // the chapter got marked as optimized
        if (state == OptimizationState.FINISHED) {
            ChapterType.clearCacheFor(chapterId, mangaId, dataFetchingEnvironment)
        }

        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterType>("ChapterDataLoader", chapterId)
    }
}

class OptimizationUpdate(
    val type: OptimizationUpdateType,
    val optimization: OptimizationType,
) {
    constructor(update: InternalOptimizationUpdate) : this(
        update.type,
        OptimizationType(update.item, update.position),
    )
}
