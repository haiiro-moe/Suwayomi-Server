package suwayomi.tachidesk.graphql.subscriptions

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UserEventBus
import suwayomi.tachidesk.server.user.UserEventType
import suwayomi.tachidesk.server.user.requireUser

class UserSubscription {
    data class UserEvent(
        val type: UserEventType,
        val otherUserId: Int?,
    )

    @RequireAuth
    fun userEvents(dataFetchingEnvironment: DataFetchingEnvironment): Flow<UserEvent> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

        return UserEventBus.eventsFor(userId).map { event ->
            UserEvent(event.type, event.otherUserIdFor[userId])
        }
    }
}
