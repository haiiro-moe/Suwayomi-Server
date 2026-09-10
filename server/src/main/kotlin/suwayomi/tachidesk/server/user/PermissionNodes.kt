package suwayomi.tachidesk.server.user

object PermissionNodes {
    const val LIBRARY_READ = "library.read"
    const val LIBRARY_CATEGORY_READ = "library.category.read"
    const val LIBRARY_CATEGORY_EDIT = "library.category.edit"
    const val UPDATES_READ = "updates.read"
    const val UPDATES_TRIGGER = "updates.trigger"
    const val UPDATES_MANAGE_CRON = "updates.manage_cron"
    const val SETTINGS_EDIT = "settings.edit"
    const val ADMIN_USERS_MANAGE = "admin.users.manage"
    const val ADMIN_ROLES_MANAGE = "admin.roles.manage"

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

    val catalog: Set<String> = setOf(
        LIBRARY_READ,
        LIBRARY_CATEGORY_READ,
        LIBRARY_CATEGORY_EDIT,
        UPDATES_READ,
        UPDATES_TRIGGER,
        UPDATES_MANAGE_CRON,
        SETTINGS_EDIT,
        ADMIN_USERS_MANAGE,
        ADMIN_ROLES_MANAGE,
        SETTINGS_NETWORK,
        SETTINGS_DATABASE,
        SETTINGS_PROXY,
        SETTINGS_WEB_UI,
        SETTINGS_DOWNLOADER,
        SETTINGS_EXTENSION,
        SETTINGS_LIBRARY_UPDATES,
        SETTINGS_AUTH,
        SETTINGS_MISC,
        SETTINGS_BACKUP,
        SETTINGS_LOCAL_SOURCE,
        SETTINGS_CLOUDFLARE,
        SETTINGS_OPDS,
        SETTINGS_KOREADER_SYNC,
        SETTINGS_WEB_VIEW,
        SETTINGS_SYNCYOMI,
    )

    fun forGroup(group: String): String = "settings.${group.lowercase().replace(' ', '_').replace('/', '_')}"
}
