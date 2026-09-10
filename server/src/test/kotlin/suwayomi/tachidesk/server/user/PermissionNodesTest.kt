package suwayomi.tachidesk.server.user

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermissionNodesTest {
    @Test
    fun `catalog contains every supported permission group`() {
        val nodes = PermissionNodes.catalog

        assertTrue(nodes.contains(PermissionNodes.LIBRARY_READ))
        assertTrue(nodes.contains(PermissionNodes.UPDATES_TRIGGER))
        assertTrue(nodes.contains(PermissionNodes.SETTINGS_BACKUP))
        assertTrue(nodes.contains(PermissionNodes.ADMIN_USERS_MANAGE))
        assertTrue(nodes.contains(PermissionNodes.ADMIN_ROLES_MANAGE))
        assertTrue(nodes.none { it == "users.manage" || it == "roles.manage" })
    }
}
