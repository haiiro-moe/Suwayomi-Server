package suwayomi.tachidesk.manga.impl.optimize

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry

private val applicationDirs: ApplicationDirs by injectLazy()

/**
 * Optimizes the downloaded files of a chapter, see [ImageOptimizer].
 *
 * Files only get replaced when the optimized version takes up less space. Originals stay untouched until the very
 * end, so a failed or canceled optimization never leaves a broken download behind.
 */
object ChapterOptimizer {
    private val logger = KotlinLogging.logger {}

    private const val TEMP_SUFFIX = ".optimizing"

    private val parallelism = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    data class Result(
        val originalSize: Long,
        val optimizedSize: Long,
    )

    class NoDownloadException : Exception("Chapter has no downloaded files")

    private fun getWorkDir(chapterId: Int): File = File(applicationDirs.tempRoot, "optimize/$chapterId")

    /**
     * @param onProgress called with the share of processed images
     * @param canCommit checked right before replacing the downloaded files, returning false discards the results
     */
    suspend fun optimize(
        mangaId: Int,
        chapterId: Int,
        onProgress: suspend (Float) -> Unit,
        canCommit: () -> Boolean,
    ): Result {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        val workDir = getWorkDir(chapterId)

        workDir.deleteRecursively()
        workDir.mkdirs()
        removeLeftovers(cbzFile, chapterFolder)

        try {
            return when {
                cbzFile.isFile -> optimizeArchive(chapterId, cbzFile, workDir, onProgress, canCommit)
                chapterFolder.isDirectory -> optimizeFolder(chapterFolder, workDir, onProgress, canCommit)
                else -> throw NoDownloadException()
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Removes temporary files of an optimization that got interrupted, e.g. by a server shutdown
     */
    private fun removeLeftovers(
        cbzFile: File,
        chapterFolder: File,
    ) {
        File(cbzFile.parentFile, "${cbzFile.name}$TEMP_SUFFIX").delete()
        chapterFolder
            .listFiles()
            .orEmpty()
            .filter { it.name.endsWith(TEMP_SUFFIX) }
            .forEach { it.delete() }
    }

    /**
     * Optimizes all [images] in parallel and returns the optimized file per image for the ones that got smaller
     */
    private suspend fun optimizeImages(
        images: List<File>,
        workDir: File,
        onProgress: suspend (Float) -> Unit,
    ): Map<File, File> {
        if (images.isEmpty()) {
            return emptyMap()
        }

        // limits the number of encoder processes running at the same time
        val semaphore = Semaphore(parallelism)
        val progressMutex = Mutex()
        var processed = 0

        return coroutineScope {
            images
                .map { image ->
                    async(Dispatchers.IO) {
                        val optimized = semaphore.withPermit { ImageOptimizer.optimize(image, File(workDir, "out")) }

                        progressMutex.withLock {
                            processed++
                            onProgress(processed.toFloat() / images.size)
                        }

                        optimized?.let { image to it }
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    private fun replaceExtension(
        name: String,
        extension: String,
    ): String {
        val baseName = name.substringBeforeLast('.', name)
        return "$baseName.$extension"
    }

    private suspend fun optimizeArchive(
        chapterId: Int,
        cbzFile: File,
        workDir: File,
        onProgress: suspend (Float) -> Unit,
        canCommit: () -> Boolean,
    ): Result {
        val extractDir = File(workDir, "in").apply { mkdirs() }
        val extractRoot = extractDir.canonicalPath + File.separator

        // keep the entries in their original order
        val entries =
            withContext(Dispatchers.IO) {
                ZipFile.builder().setFile(cbzFile).get().use { zip ->
                    zip.entries
                        .toList()
                        .filterNot { it.isDirectory }
                        .mapIndexed { index, entry ->
                            // entries are extracted under an index to be safe from odd or malicious entry names
                            val file = File(extractDir, index.toString())
                            check(file.canonicalPath.startsWith(extractRoot))
                            zip.getInputStream(entry).use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                            entry.name to file
                        }
                }
            }

        val images = entries.map { it.second }.filter(ImageOptimizer::isImage)
        val optimizedImages = optimizeImages(images, workDir, onProgress)

        val entryNames = entries.map { it.first }.toMutableSet()
        val outputEntries =
            entries.map { (name, file) ->
                val optimized = optimizedImages[file] ?: return@map name to file
                val optimizedName = replaceExtension(name, optimized.extension)

                // never let two entries end up with the same name
                if (optimizedName != name && optimizedName in entryNames) {
                    return@map name to file
                }

                entryNames -= name
                entryNames += optimizedName
                optimizedName to optimized
            }

        val tempCbz = File(cbzFile.parentFile, "${cbzFile.name}$TEMP_SUFFIX")
        withContext(Dispatchers.IO) { writeArchive(tempCbz, outputEntries) }

        val originalSize = cbzFile.length()
        val optimizedSize = tempCbz.length()

        if (optimizedSize >= originalSize || !canCommit()) {
            tempCbz.delete()
            return Result(originalSize, originalSize)
        }

        withContext(Dispatchers.IO) {
            Files.move(tempCbz.toPath(), cbzFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

        // the koreader hash is based on the file contents
        val koreaderHash = KoreaderHelper.hashContents(cbzFile)
        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[ChapterTable.koreaderHash] = koreaderHash
            }
        }

        return Result(originalSize, optimizedSize)
    }

    /**
     * Writes the entries with the smallest possible storage method each, images are usually already compressed and
     * are thus mostly stored as-is, which also makes reading them cheaper
     */
    private fun writeArchive(
        target: File,
        entries: List<Pair<String, File>>,
    ) {
        target.delete()
        ZipArchiveOutputStream(target).use { zipOut ->
            zipOut.setLevel(Deflater.BEST_COMPRESSION)

            entries.forEach { (name, file) ->
                val entry = ZipArchiveEntry(name)
                entry.time = 0L

                if (getDeflatedSize(file) < file.length()) {
                    entry.method = ZipEntry.DEFLATED
                } else {
                    entry.method = ZipEntry.STORED
                    entry.size = file.length()
                    entry.compressedSize = file.length()
                    entry.crc = getCrc(file)
                }

                zipOut.putArchiveEntry(entry)
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeArchiveEntry()
            }
        }
    }

    private fun getDeflatedSize(file: File): Long {
        val counter = CountingOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            DeflaterOutputStream(counter, deflater).use { out -> file.inputStream().use { it.copyTo(out) } }
        } finally {
            deflater.end()
        }
        return counter.count
    }

    private fun getCrc(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private class CountingOutputStream : OutputStream() {
        var count = 0L

        override fun write(b: Int) {
            count++
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            count += len
        }
    }

    private suspend fun optimizeFolder(
        chapterFolder: File,
        workDir: File,
        onProgress: suspend (Float) -> Unit,
        canCommit: () -> Boolean,
    ): Result {
        val files = chapterFolder.listFiles().orEmpty().filter { it.isFile }
        val originalSize = files.sumOf { it.length() }

        val images = files.filter(ImageOptimizer::isImage)
        val optimizedImages = optimizeImages(images, workDir, onProgress)

        if (!canCommit()) {
            return Result(originalSize, originalSize)
        }

        val existingNames = files.map { it.name }.toMutableSet()
        withContext(Dispatchers.IO) {
            optimizedImages.forEach { (image, optimized) ->
                val target = File(chapterFolder, replaceExtension(image.name, optimized.extension))

                // never overwrite a different file
                if (target.name != image.name && target.name in existingNames) {
                    return@forEach
                }

                // move into the chapter folder first to make the final rename atomic
                val staged = File(chapterFolder, "${target.name}$TEMP_SUFFIX")
                Files.move(optimized.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

                if (target.name != image.name) {
                    image.delete()
                    existingNames -= image.name
                    existingNames += target.name
                }
            }
        }

        val optimizedSize =
            chapterFolder
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .sumOf { it.length() }

        logger.debug { "optimized folder ${chapterFolder.name}: $originalSize -> $optimizedSize bytes" }

        return Result(originalSize, optimizedSize)
    }
}
