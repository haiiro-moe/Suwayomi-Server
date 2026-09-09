package suwayomi.tachidesk.server.user

object PermissionNodes {
    const val SETTINGS_NETWORK = "settings.network"
    const val SETTINGS_DATABASE = "settings.database"
    const val SETTINGS_PROXY = "settings.proxy"
    const val SETTINGS_WEB_UI = "settings.web_ui"
    const val SETTINGS_DOWNLOADER = "settings.downloader"
    const val SETTINGS_EXTENSION = "settings.extension"
    const val SETTINGS_LIBRARY_UPDATES = "settings.library_updates"
    const val SETTINGS_AUTH = "settings.auth"
    const val SETTINGS_MISC = "settings.misc"
    const val SETTINGS_BACKUP = "settings.backup"
    const val SETTINGS_LOCAL_SOURCE = "settings.local_source"
    const val SETTINGS_CLOUDFLARE = "settings.cloudflare"
    const val SETTINGS_OPDS = "settings.opds"
    const val SETTINGS_KOREADER_SYNC = "settings.koreader_sync"
    const val SETTINGS_WEB_VIEW = "settings.web_view"
    const val SETTINGS_SYNCYOMI = "settings.syncyomi"

    fun forGroup(group: String): String = "settings.${group.lowercase().replace(' ', '_').replace('/', '_')}"
}
