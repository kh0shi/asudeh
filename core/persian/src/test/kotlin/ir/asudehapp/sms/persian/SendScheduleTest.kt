package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SendScheduleTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, tehran).toInstant().toEpochMilli()

    @Test
    fun `tonight is offered in the morning`() {
        val now = at(2026, 9, 19, 10)
        val presets = SendSchedule.presets(now, tehran)
        assertEquals(SchedulePreset.TONIGHT, presets.first().preset)
        assertEquals(at(2026, 9, 19, SendSchedule.TONIGHT_HOUR), presets.first().atMillis)
    }

    @Test
    fun `tonight is dropped once it has passed`() {
        val presets = SendSchedule.presets(at(2026, 9, 19, 23), tehran)
        assertFalse(presets.any { it.preset == SchedulePreset.TONIGHT })
        assertEquals(2, presets.size)
    }

    /** ساعت ۲۱:۰۰ دقیقاً، «امشب» دیگر پیشنهاد نمی‌شود: فاصله‌اش صفر است. */
    @Test
    fun `tonight is dropped exactly at nine`() {
        val presets = SendSchedule.presets(at(2026, 9, 19, SendSchedule.TONIGHT_HOUR), tehran)
        assertFalse(presets.any { it.preset == SchedulePreset.TONIGHT })
    }

    @Test
    fun `tomorrow presets are always offered`() {
        for (hour in 0..23) {
            val presets = SendSchedule.presets(at(2026, 9, 19, hour), tehran)
            val tomorrow = presets.filter { it.preset != SchedulePreset.TONIGHT }
            assertEquals("ساعت $hour", 2, tomorrow.size)
            assertEquals(at(2026, 9, 20, SendSchedule.MORNING_HOUR), tomorrow[0].atMillis)
            assertEquals(at(2026, 9, 20, SendSchedule.AFTERNOON_HOUR), tomorrow[1].atMillis)
        }
    }

    @Test
    fun `a time in the past is refused`() {
        val now = at(2026, 9, 19, 10)
        assertNull(SendSchedule.validate(at(2026, 9, 19, 9), now))
    }

    @Test
    fun `a time less than a minute away is refused`() {
        val now = at(2026, 9, 19, 10)
        assertNull(SendSchedule.validate(now + 59_000, now))
        assertEquals(now + 60_000, SendSchedule.validate(now + 60_000, now))
    }

    @Test
    fun `a message is due at its moment and not before`() {
        val sendAt = at(2026, 9, 19, 21)
        assertFalse(SendSchedule.isDue(sendAt, sendAt - 1))
        assertTrue(SendSchedule.isDue(sendAt, sendAt))
        assertTrue(SendSchedule.isDue(sendAt, sendAt + 1))
    }

    /**
     * گوشی که چند دقیقه دیرتر بیدار شده، پیامک را می‌فرستد؛ گوشی‌ای که تا
     * فردا خاموش بوده، نمی‌فرستد و فقط نشان می‌دهد.
     */
    @Test
    fun `a short delay still sends but a long one does not`() {
        val sendAt = at(2026, 9, 19, 21)
        assertFalse(SendSchedule.isTooLate(sendAt, sendAt + 10 * 60_000))
        assertFalse(SendSchedule.isTooLate(sendAt, sendAt + SendSchedule.LATE_GRACE_MILLIS))
        assertTrue(SendSchedule.isTooLate(sendAt, sendAt + SendSchedule.LATE_GRACE_MILLIS + 1))
    }

    @Test
    fun `describe names today tomorrow and later`() {
        val now = at(2026, 9, 19, 10)
        assertEquals(ScheduleDay.TODAY, SendSchedule.describe(at(2026, 9, 19, 21), now, tehran).day)
        assertEquals(ScheduleDay.TOMORROW, SendSchedule.describe(at(2026, 9, 20, 8), now, tehran).day)
        assertEquals(ScheduleDay.LATER, SendSchedule.describe(at(2026, 9, 25, 8), now, tehran).day)
    }

    /** «فردا» یعنی روز تقویمی بعد، نه ۲۴ ساعت بعد. */
    @Test
    fun `tomorrow is a calendar day not twenty four hours`() {
        val now = at(2026, 9, 19, 23, 30)
        val soon = at(2026, 9, 20, 0, 30)
        assertEquals(ScheduleDay.TOMORROW, SendSchedule.describe(soon, now, tehran).day)
    }

    @Test
    fun `describe gives the jalali date and a padded clock`() {
        val now = at(2026, 9, 19, 10)
        val described = SendSchedule.describe(at(2026, 9, 25, 9, 5), now, tehran)
        assertEquals("09:05", described.clock())
        assertEquals(JalaliDate.of(2026, 9, 25), described.date)
    }

    @Test
    fun `a chosen day and time round trip through the picker helpers`() {
        val now = at(2026, 9, 19, 10)
        val today = SendSchedule.today(now, tehran)
        val chosen = SendSchedule.at(tehran, today + 1, 8, 30)
        assertEquals(at(2026, 9, 20, 8, 30), chosen)
    }

    /** تقویم Material نیمه‌شبِ UTC می‌دهد؛ همان روز باید برگردد. */
    @Test
    fun `the calendars utc midnight becomes the same day`() {
        val utcMidnight = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val epochDay = SendSchedule.epochDayOfUtcMillis(utcMidnight)
        assertEquals(at(2026, 9, 20, 8, 0), SendSchedule.at(tehran, epochDay, 8, 0))
    }
}
