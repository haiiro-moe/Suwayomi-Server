package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Library
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.MangaRequestTable
import suwayomi.tachidesk.server.user.model.UserTable
import java.time.Instant

object MangaRequestService {
    const val STATUS_PENDING = "PENDING"
    const val STATUS_APPROVED = "APPROVED"
    const val STATUS_DENIED = "DENIED"

    data class MangaRequest(
        val id: Int,
        val userId: Int,
        val username: String,
        val displayName: String,
        val mangaId: Int,
        val mangaTitle: String,
        val mangaThumbnailUrl: String?,
        val createdAt: Long,
        val status: String,
    )

    fun request(userId: Int, mangaId: Int): Int {
        require(UserService.hasPermission(userId, PermissionNodes.BROWSE_REQUEST)) {
            "Not allowed to request manga"
        }
        transaction(DBManager.db) {
            val existing =
                MangaRequestTable
                    .selectAll()
                    .where { (MangaRequestTable.user eq userId) and (MangaRequestTable.manga eq mangaId) }
                    .firstOrNull()
            if (existing == null) {
                MangaRequestTable.upsert(MangaRequestTable.user, MangaRequestTable.manga) {
                    it[user] = userId
                    it[manga] = mangaId
                    it[createdAt] = Instant.now().epochSecond
                    it[status] = STATUS_PENDING
                }
            }
        }
        return transaction(DBManager.db) {
            MangaRequestTable
                .selectAll()
                .where { (MangaRequestTable.user eq userId) and (MangaRequestTable.manga eq mangaId) }
                .first()[MangaRequestTable.id].value
        }
    }

    fun requests(viewerId: Int): List<MangaRequest> = transaction(DBManager.db) {
        val visible = CategoryAccessService.readableMangaIds(viewerId).toSet()

        MangaRequestTable
            .innerJoin(MangaTable)
            .innerJoin(UserTable)
            .selectAll()
            .orderBy(MangaRequestTable.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .map { row ->
                val mangaId = row[MangaRequestTable.manga].value
                MangaRequest(
                    id = row[MangaRequestTable.id].value,
                    userId = row[MangaRequestTable.user].value,
                    username = row[UserTable.username],
                    displayName = row[UserTable.displayName],
                    mangaId = mangaId,
                    mangaTitle = if (mangaId in visible) row[MangaTable.title] else "",
                    mangaThumbnailUrl = if (mangaId in visible) row[MangaTable.thumbnail_url] else null,
                    createdAt = row[MangaRequestTable.createdAt],
                    status = row[MangaRequestTable.status],
                )
            }
    }

    fun decide(viewerId: Int, requestId: Int, approve: Boolean) {
        require(UserService.hasPermission(viewerId, PermissionNodes.REQUESTS_MANAGE)) {
            "Not allowed to manage requests"
        }

        val (requestUserId, mangaId) =
            transaction(DBManager.db) {
                val request =
                    MangaRequestTable
                        .selectAll()
                        .where { MangaRequestTable.id eq requestId }
                        .firstOrNull() ?: error("Request does not exist")
                require(request[MangaRequestTable.status] == STATUS_PENDING) { "Request was already decided" }
                request[MangaRequestTable.user].value to request[MangaRequestTable.manga].value
            }

        transaction(DBManager.db) {
            MangaRequestTable.update({ MangaRequestTable.id eq requestId }) {
                it[MangaRequestTable.status] = if (approve) STATUS_APPROVED else STATUS_DENIED
            }
        }

        if (approve) {
            approveRequest(requestUserId, mangaId)
        }
    }

    private fun approveRequest(requestUserId: Int, mangaId: Int) {
        // fully update the manga from its source
        kotlinx.coroutines.runBlocking {
            Manga.updateMangaAndChapters(mangaId, updateManga = true, updateChapters = true)
        }

        // add to the default category (mirrors Library.addMangaToLibrary behavior for un-categorized adds)
        transaction(DBManager.db) {
            val defaultCategories =
                CategoryTable
                    .selectAll()
                    .where {
                        (CategoryTable.isDefault eq true) and
                            (CategoryTable.id neq Category.DEFAULT_CATEGORY_ID)
                    }.map { it[CategoryTable.id].value }

            MangaTable.update({ MangaTable.id eq mangaId }) {
                it[MangaTable.inLibrary] = true
                it[MangaTable.inLibraryAt] = Instant.now().epochSecond
            }

            defaultCategories.forEach { categoryId ->
                CategoryManga.addMangaToCategory(mangaId, categoryId)
            }
        }.apply {
            Library.handleMangaThumbnail(mangaId, true)
        }

        // queue all chapters for download
        val chapterIds =
            transaction(DBManager.db) {
                ChapterTable
                    .select(ChapterTable.id)
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map { it[ChapterTable.id].value }
            }
        if (chapterIds.isNotEmpty()) {
            DownloadManager.enqueue(DownloadManager.EnqueueInput(chapterIds = chapterIds))
        }
    }
}
