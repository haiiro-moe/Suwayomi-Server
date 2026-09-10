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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.CategoryAccessService
import suwayomi.tachidesk.server.user.requireUser

class MangaDataLoader : KotlinDataLoader<Int, MangaType> {
    override val dataLoaderName = "MangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                    val visibleMangaIds = CategoryAccessService.readableMangaIds(userId).toSet()
                    val manga =
                        MangaTable
                            .selectAll()
                            .where { (MangaTable.id inList ids) and (MangaTable.id inList visibleMangaIds) }
                            .map { MangaType(it) }
                            .associateBy { it.id }
                    ids.map { manga[it] }
                }
            }
        }
}

class MangaForCategoryDataLoader : KotlinDataLoader<Int, MangaNodeList> {
    override val dataLoaderName = "MangaForCategoryDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaNodeList> =
        DataLoaderFactory.newDataLoader<Int, MangaNodeList> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                    val visibleMangaIds = CategoryAccessService.readableMangaIds(userId).toSet()
                    val itemsByRef =
                        if (ids.contains(0)) {
                            MangaTable
                                .leftJoin(CategoryMangaTable)
                                .selectAll()
                                .where { (MangaTable.inLibrary eq true) and (MangaTable.id inList visibleMangaIds) }
                                .andWhere { CategoryMangaTable.manga.isNull() }
                                .map { MangaType(it) }
                                .let {
                                    mapOf(0 to it)
                                }
                        } else {
                            emptyMap()
                        } +
                            CategoryMangaTable
                                .innerJoin(MangaTable)
                                .selectAll()
                                .where { (CategoryMangaTable.category inList ids) and (CategoryMangaTable.manga inList visibleMangaIds) }
                                .map { Pair(it[CategoryMangaTable.category].value, MangaType(it)) }
                                .groupBy { it.first }
                                .mapValues { it.value.map { pair -> pair.second } }

                    ids.map { (itemsByRef[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class MangaForSourceDataLoader : KotlinDataLoader<Long, MangaNodeList> {
    override val dataLoaderName = "MangaForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, MangaNodeList> =
        DataLoaderFactory.newDataLoader<Long, MangaNodeList> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val mangaBySourceId =
                        MangaTable
                            .selectAll()
                            .where { MangaTable.sourceReference inList ids }
                            .map { MangaType(it) }
                            .groupBy { it.sourceId }
                    ids.map { (mangaBySourceId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
