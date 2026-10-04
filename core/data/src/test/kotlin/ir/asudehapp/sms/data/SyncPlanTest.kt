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

class SyncResumeTest {

    /** همگام‌سازی را تکه‌به‌تکه، مثل `AsudehRepository.sync`، اجرا می‌کند و بعد از [stopAfter] تکه «اپ کشته می‌شود». */
    private fun runSweep(index: MutableSet<Long>, provider: List<Long>, chunk: Int, stopAfter: Int = Int.MAX_VALUE): Int {
        val plan = SyncPlan.of(index, provider)
        var chunks = 0
        for (part in plan.missing.chunked(chunk)) {
            if (chunks == stopAfter) break
            index += part
            chunks++
        }
        return chunks
    }

    @Test
    fun `a sweep killed midway resumes where it stopped`() {
        val provider = (1L..2_000L).toList()
        val index = mutableSetOf<Long>()

        assertEquals(2, runSweep(index, provider, chunk = 500, stopAfter = 2))
        assertEquals(1_000, index.size)

        // اجرای بعدی فقط باقی‌مانده را می‌خواند، نه از اول.
        val resumed = SyncPlan.of(index, provider)
        assertEquals((1_001L..2_000L).toList(), resumed.missing)
        assertEquals(2, runSweep(index, provider, chunk = 500))
        assertEquals(provider.toSet(), index)
        assertTrue(SyncPlan.of(index, provider).missing.isEmpty())
    }

    @Test
    fun `progress reports a real percentage`() {
        assertEquals(0, SyncProgress(0, 2_000).percent)
        assertEquals(25, SyncProgress(500, 2_000).percent)
        assertEquals(99, SyncProgress(1_999, 2_000).percent)
        assertEquals(100, SyncProgress(2_000, 2_000).percent)
        assertEquals(0.5f, SyncProgress(1_000, 2_000).fraction)
    }

    @Test
    fun `nothing to do has no percentage`() {
        assertEquals(null, SyncProgress(0, 0).percent)
        assertEquals(null, SyncProgress(0, 0).fraction)
    }

    @Test
    fun `progress never leaves the zero to one range`() {
        assertEquals(100, SyncProgress(2_500, 2_000).percent)
        assertEquals(0f, SyncProgress(-1, 2_000).fraction)
    }
}
