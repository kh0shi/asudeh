package ir.asudehapp.sms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {

    @Test
    fun `history older than the newest indexed message is still imported`() {
        // اولین همگام‌سازی کامل نشده بود و یک پیامک زنده (۱۰۱) در ایندکس نوشته شد.
        val plan = SyncPlan.of(indexed = listOf(101L), provider = (1L..101L).toList())
        assertEquals((1L..100L).toList(), plan.missing)
        assertTrue(plan.vanished.isEmpty())
        assertFalse(plan.firstRun)
    }

    @Test
    fun `a message that was never indexed is picked up even below the newest id`() {
        val plan = SyncPlan.of(indexed = listOf(1L, 2L, 4L), provider = listOf(1L, 2L, 3L, 4L))
        assertEquals(listOf(3L), plan.missing)
    }

    @Test
    fun `messages deleted outside the app leave the index`() {
        val plan = SyncPlan.of(indexed = listOf(1L, 2L, 3L), provider = listOf(1L, 3L))
        assertEquals(listOf(2L), plan.vanished)
    }

    @Test
    fun `a message not yet written to the provider is never treated as deleted`() {
        val plan = SyncPlan.of(indexed = listOf(-1_234L, 5L), provider = listOf(5L))
        assertTrue(plan.vanished.isEmpty())
    }

    @Test
    fun `an empty index is the first run`() {
        assertTrue(SyncPlan.of(indexed = emptyList(), provider = listOf(1L)).firstRun)
    }
}
