package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.types.OptimizationStatus
import suwayomi.tachidesk.manga.impl.optimize.OptimizationManager
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.PermissionNodes
import java.util.concurrent.CompletableFuture

class OptimizationQuery {
    @RequirePermission(PermissionNodes.OPTIMIZATIONS_READ)
    fun optimizationStatus(): CompletableFuture<OptimizationStatus> =
        future {
            OptimizationStatus(OptimizationManager.getStatus())
        }
}
