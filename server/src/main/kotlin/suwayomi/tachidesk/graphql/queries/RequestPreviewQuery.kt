package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.CategoryAccessService
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.user.UserService
import suwayomi.tachidesk.server.user.requireUser

class RequestPreviewQuery {
    data class ChapterPreview(
        val name: String,
        val chapterNumber: Float?,
        val scanlator: String?,
    )

    data class RequestPreview(
        val manga: MangaType,
        val chapters: List<ChapterPreview>,
    )

    /**
     * Full preview for the request-only view. Fetches details and the chapter list from the source the first time a
     * manga is previewed, then serves everything from the database. Readable users do not get the online fetch.
     */
    @RequirePermission(PermissionNodes.BROWSE_REQUEST)
    fun requestPreview(
        dataFetchingEnvironment: DataFetchingEnvironment,
        mangaId: Int,
    ): RequestPreview {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

        if (UserService.hasPermission(userId, PermissionNodes.BROWSE_ADD_TO_LIBRARY) &&
            mangaId in CategoryAccessService.readableMangaIds(userId)
        ) {
            // readable users get the regular manga screen, no preview fetch
            val manga = transaction(DBManager.db) {
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.firstOrNull()
            } ?: error("Manga does not exist")
            return RequestPreview(MangaType(manga), emptyList())
        }

        val mangaEntry = transaction(DBManager.db) {
            MangaTable.selectAll().where { MangaTable.id eq mangaId }.firstOrNull() ?: error("Manga does not exist")
        }

        val needsFetch = !mangaEntry[MangaTable.initialized]
        if (needsFetch) {
            runBlocking {
                Manga.updateMangaAndChapters(mangaId, updateManga = true, updateChapters = true)
            }
        }

        val (manga, chapters) = transaction(DBManager.db) {
            val manga = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
            val chapters =
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map {
                        ChapterPreview(
                            name = it[ChapterTable.name],
                            chapterNumber = it[ChapterTable.chapter_number],
                            scanlator = it[ChapterTable.scanlator],
                        )
                    }
            manga to chapters
        }

        return RequestPreview(MangaType(manga), chapters)
    }
}
