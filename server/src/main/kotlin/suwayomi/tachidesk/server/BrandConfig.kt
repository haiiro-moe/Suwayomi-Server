package suwayomi.tachidesk.server

import java.io.File

object BrandConfig {
    val name: String
        get() = System.getenv("SUWAIRO_BRAND_NAME")?.trim().takeUnless { it.isNullOrEmpty() } ?: "Suwairo"

    val logoPath: String?
        get() = System.getenv("SUWAIRO_LOGO_PATH")?.trim().takeUnless { it.isNullOrEmpty() }

    val logoFile: File?
        get() = logoPath?.let(::File)?.takeIf { it.isFile && it.canRead() }

    val logoContentType: String
        get() =
            when (logoFile?.extension?.lowercase()) {
                "svg" -> "image/svg+xml"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
}
