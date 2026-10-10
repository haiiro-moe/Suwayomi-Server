package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.OptimizationStatus
import suwayomi.tachidesk.manga.impl.optimize.OptimizationManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class OptimizationQuery {
    @RequireAuth
    fun optimizationStatus(): CompletableFuture<OptimizationStatus> =
        future {
            OptimizationStatus(OptimizationManager.getStatus())
        }
}
