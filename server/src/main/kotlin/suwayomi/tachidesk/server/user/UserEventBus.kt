package suwayomi.tachidesk.server.user

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

enum class UserEventType {
    MESSAGES_CHANGED,
    REQUESTS_CHANGED,
}

/**
 * Lightweight in-process notifications for user facing realtime features (messages, manga requests).
 *
 * Events intentionally carry no data - clients refetch whatever they display, so permission checks stay in
 * the regular queries and nothing can leak through the subscription.
 */
object UserEventBus {
    data class Event(
        val type: UserEventType,
        /** null means the event is relevant to every user */
        val recipients: Set<Int>?,
        /** the other participant of a conversation for [UserEventType.MESSAGES_CHANGED] */
        val otherUserIdFor: Map<Int, Int> = emptyMap(),
    )

    private val events =
        MutableSharedFlow<Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun eventsFor(userId: Int): Flow<Event> =
        events.asSharedFlow().filter { event -> event.recipients == null || userId in event.recipients }

    fun messagesChanged(
        userId: Int,
        otherUserId: Int,
    ) {
        events.tryEmit(
            Event(
                type = UserEventType.MESSAGES_CHANGED,
                recipients = setOf(userId, otherUserId),
                otherUserIdFor = mapOf(userId to otherUserId, otherUserId to userId),
            ),
        )
    }

    fun requestsChanged() {
        events.tryEmit(Event(type = UserEventType.REQUESTS_CHANGED, recipients = null))
    }
}
