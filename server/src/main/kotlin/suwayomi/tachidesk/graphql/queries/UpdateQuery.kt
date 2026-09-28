package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.PermissionNodes
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    @RequirePermission(PermissionNodes.UPDATES_READ)
    fun updateStatus(): CompletableFuture<UpdateStatus> =
        future {
            UpdateStatus(updater.status.first())
        }

    @RequirePermission(PermissionNodes.UPDATES_READ)
    fun libraryUpdateStatus(): CompletableFuture<LibraryUpdateStatus> =
        future {
            LibraryUpdateStatus(updater.getStatus())
        }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    @RequirePermission(PermissionNodes.UPDATES_READ)
    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
}
