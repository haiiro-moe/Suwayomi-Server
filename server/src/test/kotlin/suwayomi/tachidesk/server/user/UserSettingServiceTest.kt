package suwayomi.tachidesk.server.user

import org.junit.jupiter.api.Test
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserSettingServiceTest : ApplicationTest() {
    @Test
    fun `user settings are stored per user`() {
        val firstUser = UserService.ownerUserId() ?: error("Owner user missing")
        UserSettingService.set(firstUser, mapOf("themeMode" to "dark"))

        assertEquals("dark", UserSettingService.read(firstUser)["themeMode"])
    }

    @Test
    fun `unsupported user setting is rejected`() {
        val userId = UserService.ownerUserId() ?: error("Owner user missing")
        assertFailsWith<IllegalArgumentException> {
            UserSettingService.set(userId, mapOf("serverPort" to "4567"))
        }
    }
}
