package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.MangaRequestService
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.user.requireUser

class RequestMutation {
    data class RequestMangaInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    data class RequestMangaPayload(
        val clientMutationId: String?,
        val requestId: Int,
    )

    @RequirePermission(PermissionNodes.BROWSE_REQUEST)
    fun requestManga(dataFetchingEnvironment: DataFetchingEnvironment, input: RequestMangaInput): RequestMangaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val requestId = MangaRequestService.request(userId, input.mangaId)
        return RequestMangaPayload(input.clientMutationId, requestId)
    }

    data class DecideMangaRequestInput(
        val clientMutationId: String? = null,
        val requestId: Int,
        val approve: Boolean,
    )

    data class DecideMangaRequestPayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequirePermission(PermissionNodes.REQUESTS_MANAGE)
    fun decideMangaRequest(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DecideMangaRequestInput,
    ): DecideMangaRequestPayload {
        val viewerId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        MangaRequestService.decide(viewerId, input.requestId, input.approve)
        return DecideMangaRequestPayload(input.clientMutationId, true)
    }
}
