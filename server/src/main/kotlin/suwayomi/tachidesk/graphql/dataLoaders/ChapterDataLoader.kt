/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.CategoryAccessService
import suwayomi.tachidesk.server.user.UserChapterStateService
import suwayomi.tachidesk.server.user.requireUser

private fun visibleMangaIds(userId: Int, ids: List<Int>): List<Int> =
    ids.intersect(CategoryAccessService.readableMangaIds(userId).toSet()).toList()

class ChapterDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "ChapterDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                    val visibleIds =
                        ChapterTable
                            .select(ChapterTable.id)
                            .where { ChapterTable.manga inList CategoryAccessService.readableMangaIds(userId) }
                            .map { it[ChapterTable.id].value }
                            .intersect(ids.toSet())
                            .toList()
                    val stateByChapterId = UserChapterStateService.getForUser(userId, visibleIds)
                    val chapters =
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id inList visibleIds }
                            .map { row ->
                                ChapterType(row).withUserState(stateByChapterId[row[ChapterTable.id].value])
                            }
                            .associateBy { it.id }
                    ids.map { chapters[it] }
                }
            }
        }
}

class ChaptersForMangaDataLoader : KotlinDataLoader<Int, ChapterNodeList> {
    override val dataLoaderName = "ChaptersForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                    val rows =
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.manga inList visibleMangaIds(userId, ids) }
                            .toList()
                    val stateByChapterId = UserChapterStateService.getForUser(userId, rows.map { it[ChapterTable.id].value })
                    val chaptersByMangaId =
                        rows
                            .map { row -> ChapterType(row).withUserState(stateByChapterId[row[ChapterTable.id].value]) }
                            .groupBy { it.mangaId }
                    ids.map { (chaptersByMangaId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

data class MangaChapterStats(
    val unreadCount: Int,
    val downloadCount: Int,
    val bookmarkCount: Int,
)

class ChapterFlagCountForMangaDataLoader : KotlinDataLoader<Int, MangaChapterStats> {
    override val dataLoaderName = "ChapterFlagCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaChapterStats> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val rows = ChapterTable.selectAll().where { ChapterTable.manga inList ids }.toList()
                    val states = UserChapterStateService.getForUser(userId, rows.map { it[ChapterTable.id].value })
                    val statsByMangaId = rows.groupBy { it[ChapterTable.manga].value }.mapValues { (_, chapters) ->
                        MangaChapterStats(
                            unreadCount = chapters.count { !states[it[ChapterTable.id].value]?.isRead.orFalse() },
                            downloadCount = chapters.count { it[ChapterTable.isDownloaded] },
                            bookmarkCount = chapters.count { states[it[ChapterTable.id].value]?.isBookmarked.orFalse() },
                        )
                    }
                    ids.map { statsByMangaId[it] ?: MangaChapterStats(0, 0, 0) }
                }
            }
        }

    private fun Boolean?.orFalse(): Boolean = this == true
}

class HasDuplicateChaptersForMangaDataLoader : KotlinDataLoader<Int, Boolean> {
    override val dataLoaderName = "HasDuplicateChaptersForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Boolean> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val duplicatedChapterCountByMangaId =
                        ChapterTable
                            .select(ChapterTable.manga, ChapterTable.chapter_number, ChapterTable.chapter_number.count())
                            .where {
                                (
                                    ChapterTable.manga inList
                                        ids
                                ) and
                                    (ChapterTable.chapter_number greaterEq 0f)
                            }.groupBy(ChapterTable.manga, ChapterTable.chapter_number)
                            .having { ChapterTable.chapter_number.count() greater 1 }
                            .associate { it[ChapterTable.manga].value to it[ChapterTable.chapter_number.count()] }

                    ids.map { duplicatedChapterCountByMangaId.contains(it) }
                }
            }
        }
}

private fun userReadChapters(
    graphQLContext: GraphQLContext,
    ids: List<Int>,
    latest: Boolean,
): List<ChapterType?> {
    val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
    val rows = transaction {
        ChapterTable.selectAll().where { ChapterTable.manga inList ids }.toList()
    }
    val states = UserChapterStateService.getForUser(userId, rows.map { it[ChapterTable.id].value })
    val selected = rows.filter { row ->
        val state = states[row[ChapterTable.id].value]
        state != null && (if (latest) state.isRead else state.lastReadAt > 0)
    }.groupBy { it[ChapterTable.manga].value }.mapValues { (_, chapters) ->
        chapters.maxWithOrNull(compareBy({ states[it[ChapterTable.id].value]?.lastReadAt ?: 0 }, { it[ChapterTable.sourceOrder] }))
    }
    return ids.map { selected[it]?.let(::ChapterType) }
}

class LastReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LastReadChapterForMangaDataLoader"
    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids -> future { userReadChapters(graphQLContext, ids, latest = false) } }
}

class LatestReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestReadChapterForMangaDataLoader"
    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids -> future { userReadChapters(graphQLContext, ids, latest = true) } }
}

class FirstUnreadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "FirstUnreadChapterForMangaDataLoader"
    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                val rows = transaction { ChapterTable.selectAll().where { ChapterTable.manga inList ids }.toList() }
                val states = UserChapterStateService.getForUser(userId, rows.map { it[ChapterTable.id].value })
                ids.map { mangaId -> rows.filter { it[ChapterTable.manga].value == mangaId && states[it[ChapterTable.id].value]?.isRead != true }.minByOrNull { it[ChapterTable.sourceOrder] }?.let(::ChapterType) }
            }
        }
}

class LatestFetchedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestFetchedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val latestFetchedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) }
                            .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> latestFetchedChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class LatestUploadedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestUploadedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val latestUploadedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) }
                            .orderBy(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> latestUploadedChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class HighestNumberedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "HighestNumberedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val highestNumberedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) and (ChapterTable.chapter_number greater 0f) }
                            .orderBy(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id ->
                        highestNumberedChaptersByMangaId[id]
                            ?.firstOrNull()
                            ?.let { chapter -> ChapterType(chapter) }
                    }
                }
            }
        }
}
