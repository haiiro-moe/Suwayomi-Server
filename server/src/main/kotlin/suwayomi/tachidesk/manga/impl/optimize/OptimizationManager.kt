package suwayomi.tachidesk.manga.impl.optimize

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationQueueItem
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationState
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationStatus
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdate
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdateType
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizationUpdates
import suwayomi.tachidesk.manga.impl.optimize.model.OptimizerStatus
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class ChapterOptimizingException(
    chapterId: Int,
) : IllegalStateException("Chapter $chapterId is queued for optimization and can't be opened until the optimization is done")

/**
 * Queue of downloaded chapters whose files get optimized one after another, see [ChapterOptimizer].
 *
 * Chapters can't be read while they are in the queue (see [ensureAccessible]), regardless of the user's permissions,
 * since their files might get replaced at any moment.
 */
@OptIn(FlowPreview::class)
object OptimizationManager {
    private const val MAX_TRIES = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = CopyOnWriteArrayList<OptimizationQueueItem>()
    private val pendingUpdates = CopyOnWriteArraySet<OptimizationUpdate>()

    private val jobMutex = Mutex()
    private var job: Job? = null

    @Volatile
    private var activeItem: OptimizationQueueItem? = null

    /** Whether chapters get queued automatically once their download finished */
    private val optimizeAfterDownload: Boolean =
        System
            .getenv("SUWAIRO_OPTIMIZE_AFTER_DOWNLOAD")
            ?.trim()
            ?.lowercase()
            ?.let { it != "false" && it != "0" } ?: true

    private const val QUEUE_KEY = "optimizationQueueKey"
    private val sharedPreferences =
        Injekt.get<Application>().getSharedPreferences(OptimizationManager::class.jvmName, Context.MODE_PRIVATE)

    private val saveQueueFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val updatesFlow = MutableSharedFlow<OptimizationUpdates>()
    val updates = updatesFlow.onStart { emit(getUpdates(addInitial = true)) }

    init {
        saveQueueFlow.onEach { saveQueue() }.launchIn(scope)

        scope.launch {
            notifyFlow.sample(1.seconds).collect { emitUpdates() }
        }
    }

    private fun saveQueue() {
        // a string keeps the order of the queue, unlike a string set
        sharedPreferences
            .edit()
            .putString(QUEUE_KEY, queue.joinToString(",") { it.chapterId.toString() })
            .commit()
    }

    private fun triggerSaveQueue() {
        scope.launch { saveQueueFlow.emit(Unit) }
    }

    private fun loadQueue(): List<Int> =
        sharedPreferences
            .getString(QUEUE_KEY, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toIntOrNull() }

    fun restoreAndResume() {
        // read before anything else can save the queue
        val savedQueue = loadQueue()

        scope.launch {
            enqueue(savedQueue)

            if (queue.isNotEmpty()) {
                logger.info { "Restored optimization queue with ${queue.size} chapters" }
            }

            // log missing tools early instead of on the first optimization
            ImageOptimizer.logMissingTools()
        }
    }

    fun isQueued(chapterId: Int): Boolean = queue.any { it.chapterId == chapterId }

    /**
     * Throws in case the chapter is currently queued for optimization.
     *
     * Has to be called before handing out any content of a downloaded chapter.
     */
    fun ensureAccessible(chapterId: Int) {
        if (isQueued(chapterId)) {
            throw ChapterOptimizingException(chapterId)
        }
    }

    private fun getOptimizerStatus(): OptimizerStatus = if (job?.isActive == true) OptimizerStatus.Started else OptimizerStatus.Stopped

    fun getStatus(): OptimizationStatus = OptimizationStatus(getOptimizerStatus(), queue.toList())

    private fun getUpdates(addInitial: Boolean = false): OptimizationUpdates =
        OptimizationUpdates(
            getOptimizerStatus(),
            pendingUpdates.toList(),
            if (addInitial) queue.toList() else null,
        )

    private fun emitUpdates() {
        val updates = getUpdates()
        pendingUpdates.clear()
        scope.launch { updatesFlow.emit(updates) }
    }

    private fun notify(
        updates: List<OptimizationUpdate> = emptyList(),
        immediate: Boolean = false,
    ) {
        val outdatedUpdates = pendingUpdates.filter { pending -> updates.any { it.item.chapterId == pending.item.chapterId } }
        pendingUpdates.removeAll(outdatedUpdates.toSet())
        pendingUpdates.addAll(updates)

        if (immediate) {
            emitUpdates()
            return
        }

        scope.launch { notifyFlow.emit(Unit) }
    }

    private fun notify(
        type: OptimizationUpdateType,
        item: OptimizationQueueItem,
        immediate: Boolean = false,
    ) {
        notify(listOf(OptimizationUpdate(type, item, queue.indexOf(item))), immediate)
    }

    /**
     * Queues the downloaded, not yet optimized chapters of [chapterIds]. Chapters whose optimization failed are retried.
     */
    fun enqueue(chapterIds: List<Int>) {
        if (chapterIds.isEmpty()) return

        val chapters =
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .select(ChapterTable.id, ChapterTable.manga)
                    .where {
                        (ChapterTable.id inList chapterIds) and
                            (ChapterTable.isDownloaded eq true) and
                            (ChapterTable.isOptimized eq false) and
                            (MangaTable.sourceReference neq LocalSource.ID)
                    }.associate { it[ChapterTable.id].value to it[ChapterTable.manga].value }
            }

        // keep the requested order
        val toQueue = chapterIds.distinct().mapNotNull { chapterId -> chapters[chapterId]?.let { chapterId to it } }

        val updates =
            toQueue.mapNotNull { (chapterId, mangaId) ->
                val existingItem = queue.firstOrNull { it.chapterId == chapterId }

                if (existingItem == null) {
                    val item = OptimizationQueueItem(chapterId, mangaId)
                    queue.add(item)
                    return@mapNotNull OptimizationUpdate(OptimizationUpdateType.QUEUED, item, queue.size - 1)
                }

                if (existingItem.state == OptimizationState.Error) {
                    existingItem.state = OptimizationState.Queued
                    existingItem.progress = 0f
                    existingItem.tries = 0
                    return@mapNotNull OptimizationUpdate(OptimizationUpdateType.QUEUED, existingItem, queue.indexOf(existingItem))
                }

                null
            }

        if (updates.isEmpty()) {
            return
        }

        logger.debug { "Queued ${updates.size} chapters for optimization" }

        triggerSaveQueue()
        notify(updates)
        start()
    }

    /**
     * Called by the downloader for a freshly downloaded chapter before it gets marked as downloaded, so that it never
     * becomes readable before it got optimized.
     */
    fun enqueueDownloaded(
        chapterId: Int,
        mangaId: Int,
    ) {
        if (!optimizeAfterDownload || isQueued(chapterId)) {
            return
        }

        val isOptimized =
            transaction {
                ChapterTable
                    .select(ChapterTable.isOptimized)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.get(ChapterTable.isOptimized)
            } ?: return

        if (isOptimized) {
            return
        }

        val item = OptimizationQueueItem(chapterId, mangaId)
        queue.add(item)
        triggerSaveQueue()
        notify(OptimizationUpdateType.QUEUED, item)
        start()
    }

    /**
     * Removes the chapters from the queue. An optimization in progress for one of them gets canceled without
     * touching the downloaded files.
     */
    suspend fun dequeue(chapterIds: List<Int>) {
        val items = queue.filter { it.chapterId in chapterIds }
        if (items.isEmpty()) return

        queue.removeAll(items.toSet())
        triggerSaveQueue()
        notify(items.map { OptimizationUpdate(OptimizationUpdateType.DEQUEUED, it, -1) })

        if (activeItem in items) {
            jobMutex.withLock {
                job?.cancelAndJoin()
            }
            start()
        }
    }

    fun reorder(
        chapterId: Int,
        to: Int,
    ) {
        require(to >= 0) { "'to' must be over or equal to 0" }

        val item = queue.find { it.chapterId == chapterId } ?: return

        queue -= item
        queue.add(to.coerceAtMost(queue.size), item)

        triggerSaveQueue()
        notify(OptimizationUpdateType.POSITION, item)
    }

    fun start() {
        scope.launch {
            jobMutex.withLock {
                if (job?.isActive == true || queue.none { it.state == OptimizationState.Queued }) {
                    return@withLock
                }

                job =
                    scope.launch { run() }.also {
                        it.invokeOnCompletion { notify(immediate = true) }
                    }
                notify(immediate = true)
            }
        }
    }

    suspend fun stop() {
        jobMutex.withLock {
            job?.cancelAndJoin()
        }
        notify(immediate = true)
    }

    suspend fun clear() {
        stop()
        val removedItems = queue.toList()
        queue.clear()
        triggerSaveQueue()
        notify(removedItems.map { OptimizationUpdate(OptimizationUpdateType.DEQUEUED, it, -1) }, immediate = true)
    }

    private suspend fun run() {
        while (currentCoroutineContext().isActive) {
            val item = queue.firstOrNull { it.state == OptimizationState.Queued } ?: break

            activeItem = item
            item.state = OptimizationState.Optimizing
            item.progress = 0f
            notify(OptimizationUpdateType.PROGRESS, item, immediate = true)

            try {
                val result =
                    ChapterOptimizer.optimize(
                        mangaId = item.mangaId,
                        chapterId = item.chapterId,
                        onProgress = { progress ->
                            item.progress = progress
                            notify(OptimizationUpdateType.PROGRESS, item)
                        },
                        canCommit = { item in queue && job?.isActive == true },
                    )

                if (item !in queue) {
                    continue
                }

                transaction {
                    ChapterTable.update({ ChapterTable.id eq item.chapterId }) {
                        it[isOptimized] = true
                    }
                }

                item.state = OptimizationState.Finished
                item.progress = 1f
                item.originalSize = result.originalSize
                item.optimizedSize = result.optimizedSize
                notify(OptimizationUpdateType.FINISHED, item, immediate = true)
                queue -= item
                triggerSaveQueue()

                logger.info {
                    "Optimized chapter ${item.chapterId}: ${result.originalSize} -> ${result.optimizedSize} bytes"
                }
            } catch (e: CancellationException) {
                if (item in queue) {
                    item.state = OptimizationState.Queued
                    item.progress = 0f
                    notify(OptimizationUpdateType.PROGRESS, item)
                }
                throw e
            } catch (e: ChapterOptimizer.NoDownloadException) {
                logger.warn(e) { "Optimization of chapter ${item.chapterId} failed" }
                item.tries = MAX_TRIES
                item.state = OptimizationState.Error
                notify(OptimizationUpdateType.ERROR, item, immediate = true)
            } catch (e: Exception) {
                logger.warn(e) { "Optimization of chapter ${item.chapterId} failed" }
                item.tries++
                item.progress = 0f
                item.state = if (item.tries >= MAX_TRIES) OptimizationState.Error else OptimizationState.Queued
                notify(
                    if (item.state == OptimizationState.Error) OptimizationUpdateType.ERROR else OptimizationUpdateType.PROGRESS,
                    item,
                    immediate = true,
                )
            } finally {
                activeItem = null
            }
        }
    }
}
