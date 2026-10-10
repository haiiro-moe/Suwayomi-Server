@file:Suppress("unused")

package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ChapterOptimizationReorder
import suwayomi.tachidesk.graphql.types.OptimizationStatus
import suwayomi.tachidesk.manga.impl.optimize.OptimizationManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class OptimizationMutation {
    data class OptimizationStatusPayload(
        val clientMutationId: String?,
        val optimizationStatus: OptimizationStatus,
    )

    private fun payload(clientMutationId: String?) =
        OptimizationStatusPayload(clientMutationId, OptimizationStatus(OptimizationManager.getStatus()))

    data class EnqueueChapterOptimizationsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    @RequireAuth
    fun enqueueChapterOptimizations(input: EnqueueChapterOptimizationsInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            OptimizationManager.enqueue(input.ids)
            payload(input.clientMutationId)
        }

    data class DequeueChapterOptimizationsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    @RequireAuth
    fun dequeueChapterOptimizations(input: DequeueChapterOptimizationsInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            OptimizationManager.dequeue(input.ids)
            payload(input.clientMutationId)
        }

    data class ReorderChapterOptimizationsInput(
        val clientMutationId: String? = null,
        val reorders: List<ChapterOptimizationReorder>,
    )

    @RequireAuth
    fun reorderChapterOptimizations(input: ReorderChapterOptimizationsInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            input.reorders.forEach { OptimizationManager.reorder(it.chapterId, it.to) }
            payload(input.clientMutationId)
        }

    data class StartOptimizerInput(
        val clientMutationId: String? = null,
    )

    @RequireAuth
    fun startOptimizer(input: StartOptimizerInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            OptimizationManager.start()
            payload(input.clientMutationId)
        }

    data class StopOptimizerInput(
        val clientMutationId: String? = null,
    )

    @RequireAuth
    fun stopOptimizer(input: StopOptimizerInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            OptimizationManager.stop()
            payload(input.clientMutationId)
        }

    data class ClearOptimizerInput(
        val clientMutationId: String? = null,
    )

    @RequireAuth
    fun clearOptimizer(input: ClearOptimizerInput): CompletableFuture<OptimizationStatusPayload> =
        future {
            OptimizationManager.clear()
            payload(input.clientMutationId)
        }
}
