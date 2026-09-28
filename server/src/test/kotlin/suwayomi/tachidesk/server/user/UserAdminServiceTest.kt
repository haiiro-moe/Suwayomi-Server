package suwayomi.tachidesk.server.user

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.assertFailsWith

class UserAdminServiceTest : ApplicationTest() {
    @Test
    fun `creating a user requires an existing role`() {
        assertThrows<IllegalArgumentException> {
            UserAdminService.createUser("missing-role", "password", "Missing role", -1)
        }
    }

    @Test
    fun `owner role name is reserved`() {
        assertFailsWith<IllegalArgumentException> {
            UserAdminService.createRole("owner", "", emptySet())
        }
    }
}
