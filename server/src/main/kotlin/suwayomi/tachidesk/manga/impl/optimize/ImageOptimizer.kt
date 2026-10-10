package suwayomi.tachidesk.manga.impl.optimize

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Shrinks single images with external command line tools.
 *
 * Every available encoder produces a candidate and the smallest one wins. Lossless candidates are preferred when they
 * are close to the lossy one in size, since they don't cost any quality. Required tools (Ubuntu/Debian packages):
 * - `cwebp` (webp): lossy and lossless WebP encoding
 * - `jpegtran` (libjpeg-turbo-progs): lossless JPEG optimization
 * - `optipng` (optipng): lossless PNG optimization
 *
 * Missing tools are skipped, so the optimizer keeps working with whatever is installed.
 */
object ImageOptimizer {
    private val logger = KotlinLogging.logger {}

    /** WebP quality for lossy re-encoding, high enough to keep screentones and line art intact */
    private val webpQuality: Int =
        System
            .getenv("SUWAIRO_OPTIMIZE_WEBP_QUALITY")
            ?.toIntOrNull()
            ?.coerceIn(1, 100) ?: 85

    /** Whether images may be converted to WebP, otherwise only lossless optimizations in the original format are done */
    private val allowWebp: Boolean =
        System
            .getenv("SUWAIRO_OPTIMIZE_ALLOW_WEBP")
            ?.trim()
            ?.lowercase()
            ?.let { it != "false" && it != "0" } ?: true

    /** A lossless candidate is used over a lossy one as long as it is at most this much bigger */
    private const val LOSSLESS_PREFERENCE_FACTOR = 1.05

    /** cwebp can't encode images exceeding this size in either dimension */
    private const val WEBP_MAX_DIMENSION = 16383

    private const val PROCESS_TIMEOUT_MINUTES = 5L

    class Tools(
        val cwebp: String?,
        val jpegtran: String?,
        val optipng: String?,
    ) {
        val missing: List<String>
            get() =
                listOfNotNull(
                    "cwebp (package: webp)".takeIf { cwebp == null },
                    "jpegtran (package: libjpeg-turbo-progs)".takeIf { jpegtran == null },
                    "optipng (package: optipng)".takeIf { optipng == null },
                )
    }

    val tools: Tools by lazy {
        Tools(
            cwebp = findExecutable("cwebp"),
            jpegtran = findExecutable("jpegtran"),
            optipng = findExecutable("optipng"),
        ).also {
            if (it.missing.isNotEmpty()) {
                logger.warn { "Image optimization tools missing, images will be optimized less: ${it.missing.joinToString()}" }
            }
        }
    }

    fun logMissingTools() {
        // the tools get detected and missing ones logged on first access
        tools.missing
    }

    private fun findExecutable(name: String): String? =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath

    private data class Candidate(
        val file: File,
        val lossless: Boolean,
    )

    fun getImageType(file: File): ImageType? = runCatching { ImageUtil.findImageType { file.inputStream() } }.getOrNull()

    fun isImage(file: File): Boolean = getImageType(file) != null

    /**
     * Returns the smallest encoding of [input], or null if none of the candidates is smaller than the original.
     *
     * The returned file is located in [workDir] and its extension matches its format.
     */
    suspend fun optimize(
        input: File,
        workDir: File,
    ): File? {
        val type = getImageType(input) ?: return null
        val baseName = "${input.nameWithoutExtension}-${System.nanoTime()}"
        workDir.mkdirs()

        val candidates =
            when (type) {
                ImageType.JPEG -> {
                    listOfNotNull(
                        jpegtran(input, File(workDir, "$baseName.jpg")),
                        cwebpLossy(input, File(workDir, "$baseName.webp")),
                    )
                }

                ImageType.PNG -> {
                    listOfNotNull(
                        optipng(input, File(workDir, "$baseName.png")),
                        cwebpLossless(input, File(workDir, "$baseName.lossless.webp")),
                        cwebpLossy(input, File(workDir, "$baseName.webp")),
                    )
                }

                // lossy WebP is already as small as it gets without further quality loss
                ImageType.WEBP -> {
                    if (isLosslessWebp(input)) {
                        listOfNotNull(cwebpLossy(input, File(workDir, "$baseName.webp")))
                    } else {
                        emptyList()
                    }
                }

                else -> {
                    emptyList()
                }
            }

        val best = pickBest(candidates)
        candidates.filter { it != best }.forEach { it.file.delete() }

        if (best == null || best.file.length() >= input.length()) {
            best?.file?.delete()
            return null
        }

        return best.file
    }

    private fun pickBest(candidates: List<Candidate>): Candidate? {
        val smallest = candidates.minByOrNull { it.file.length() } ?: return null
        if (smallest.lossless) {
            return smallest
        }

        val smallestLossless = candidates.filter { it.lossless }.minByOrNull { it.file.length() } ?: return smallest
        val isLosslessCloseEnough = smallestLossless.file.length() <= smallest.file.length() * LOSSLESS_PREFERENCE_FACTOR

        return if (isLosslessCloseEnough) smallestLossless else smallest
    }

    private suspend fun jpegtran(
        input: File,
        output: File,
    ): Candidate? {
        val jpegtran = tools.jpegtran ?: return null

        return run(
            listOf(jpegtran, "-copy", "none", "-optimize", "-progressive", "-outfile", output.absolutePath, input.absolutePath),
            output,
            ImageType.JPEG,
        )?.let { Candidate(it, lossless = true) }
    }

    private suspend fun optipng(
        input: File,
        output: File,
    ): Candidate? {
        val optipng = tools.optipng ?: return null

        return run(
            listOf(optipng, "-quiet", "-o2", "-strip", "all", "-out", output.absolutePath, input.absolutePath),
            output,
            ImageType.PNG,
        )?.let { Candidate(it, lossless = true) }
    }

    private suspend fun cwebpLossy(
        input: File,
        output: File,
    ): Candidate? {
        val cwebp = tools.cwebp?.takeIf { allowWebp && fitsWebp(input) } ?: return null

        return run(
            listOf(
                cwebp,
                "-quiet",
                "-mt",
                "-m",
                "6",
                "-q",
                webpQuality.toString(),
                "-metadata",
                "none",
                input.absolutePath,
                "-o",
                output.absolutePath,
            ),
            output,
            ImageType.WEBP,
        )?.let { Candidate(it, lossless = false) }
    }

    private suspend fun cwebpLossless(
        input: File,
        output: File,
    ): Candidate? {
        val cwebp = tools.cwebp?.takeIf { allowWebp && fitsWebp(input) } ?: return null

        return run(
            listOf(cwebp, "-quiet", "-mt", "-lossless", "-z", "6", "-metadata", "none", input.absolutePath, "-o", output.absolutePath),
            output,
            ImageType.WEBP,
        )?.let { Candidate(it, lossless = true) }
    }

    private fun fitsWebp(input: File): Boolean {
        val (width, height) = getDimensions(input) ?: return true
        return width <= WEBP_MAX_DIMENSION && height <= WEBP_MAX_DIMENSION
    }

    private fun getDimensions(input: File): Pair<Int, Int>? =
        runCatching {
            ImageIO.createImageInputStream(input).use { stream ->
                val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
                try {
                    reader.input = stream
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull()

    /** A WebP file stores lossless data in a "VP8L" chunk, either right after the header or after the extended "VP8X" one */
    private fun isLosslessWebp(input: File): Boolean =
        runCatching {
            input.inputStream().use { stream ->
                val header = ByteArray(64)
                val read = stream.read(header)
                String(header, 0, read.coerceAtLeast(0), Charsets.ISO_8859_1).contains("VP8L")
            }
        }.getOrDefault(false)

    private suspend fun run(
        command: List<String>,
        output: File,
        expectedType: ImageType,
    ): File? {
        val success =
            runInterruptible(Dispatchers.IO) {
                val process =
                    try {
                        ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start()
                    } catch (e: IOException) {
                        logger.warn(e) { "Could not run ${command.first()}" }
                        return@runInterruptible false
                    }
                try {
                    if (!process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                        logger.warn { "${command.first()} timed out" }
                        false
                    } else {
                        process.exitValue() == 0
                    }
                } finally {
                    // also stops the tool in case the optimization got canceled
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }

        val isValid = success && output.isFile && output.length() > 0 && getImageType(output) == expectedType
        if (!isValid) {
            logger.debug { "${command.first()} did not produce a usable ${output.extension} image" }
            output.delete()
            return null
        }

        return output
    }
}
