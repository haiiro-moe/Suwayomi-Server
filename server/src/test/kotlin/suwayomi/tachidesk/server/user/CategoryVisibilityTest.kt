package suwayomi.tachidesk.server.user

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryVisibilityTest {
    @Test
    fun `read access is independent from edit access`() {
        val access = CategoryAccess(read = true, edit = false)

        assertTrue(access.canRead)
        assertFalse(access.canEdit)
    }

    @Test
    fun `edit access implies read access`() {
        val access = CategoryAccess(read = false, edit = true)

        assertTrue(access.canRead)
        assertTrue(access.canEdit)
    }
}
