package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MessageGroupingTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val utc = ZoneId.of("UTC")

    private fun at(zone: ZoneId, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `day changes at local midnight, not utc midnight`() {
        // ۲۳:۳۰ و ۰۰:۳۰ تهران دو روزند، با اینکه در UTC هر دو یک روزند.
        val before = at(tehran, 25, 23, 30)
        val after = at(tehran, 26, 0, 30)
        assertFalse(MessageGrouping.sameDay(before, after, tehran))
        assertTrue(MessageGrouping.sameDay(before, after, utc))
    }

    @Test
    fun `today, yesterday and older`() {
        val now = at(tehran, 26, 10)
        assertEquals(DayLabel.TODAY, MessageGrouping.label(at(tehran, 26, 0, 1), now, tehran))
        assertEquals(DayLabel.YESTERDAY, MessageGrouping.label(at(tehran, 25, 23, 59), now, tehran))
        assertEquals(DayLabel.DATE, MessageGrouping.label(at(tehran, 24, 12), now, tehran))
    }

    @Test
    fun `consecutive messages of one side within three minutes form a run`() {
        val first = BubbleMeta(at(tehran, 26, 10, 0), outgoing = false, sender = "0912")
        assertTrue(MessageGrouping.sameRun(first, first.copy(millis = first.millis + 179_000), tehran))
        assertFalse(MessageGrouping.sameRun(first, first.copy(millis = first.millis + 180_000), tehran))
    }

    @Test
    fun `side, sender or day change ends a run`() {
        val first = BubbleMeta(at(tehran, 25, 23, 59), outgoing = false, sender = "0912")
        val soon = first.millis + 60_000
        assertFalse(MessageGrouping.sameRun(first, first.copy(millis = soon, outgoing = true), tehran))
        assertFalse(MessageGrouping.sameRun(first, first.copy(millis = soon, sender = "0935"), tehran))
        // یک دقیقه بعد ولی روز بعد.
        assertFalse(MessageGrouping.sameRun(first, first.copy(millis = soon), tehran))
    }
}
