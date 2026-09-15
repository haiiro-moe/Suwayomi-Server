package suwayomi.tachidesk.util

import org.junit.jupiter.api.Test

class HASchedulerTest {
    @Test
    fun `cron expressions are validated using cron4j`() {
        kotlin.test.assertNull(validateCronExpression("0 0 * * *"))
        kotlin.test.assertNotNull(validateCronExpression("not a cron expression"))
    }
}
