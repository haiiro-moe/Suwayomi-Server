package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.MangaRequestService
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.user.requireUser

class RequestQuery {
    data class MangaRequestType(
        val id: Int,
        val userId: Int,
        val username: String,
        val displayName: String,
        val mangaId: Int,
        val mangaTitle: String?,
        val mangaThumbnailUrl: String?,
        val createdAt: Long,
        val status: String,
    )

    @RequirePermission(PermissionNodes.REQUESTS_READ)
    fun mangaRequests(dataFetchingEnvironment: DataFetchingEnvironment): List<MangaRequestType> {
        val viewerId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return MangaRequestService.requests(viewerId).map {
            MangaRequestType(
                it.id,
                it.userId,
                it.username,
                it.displayName,
                it.mangaId,
                it.mangaTitle.ifEmpty { null },
                it.mangaThumbnailUrl,
                it.createdAt,
                it.status,
            )
        }
    }
}
