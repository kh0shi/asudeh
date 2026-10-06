package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarMonthTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val utc = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, zone: ZoneId = tehran): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val minute = 60_000L

    @Test
    fun `gregorian month runs from the first to the first of next month`() {
        val range = CalendarMonth.containing(at(2026, 10, 6, 14), jalali = false, zone = tehran)
        assertEquals(at(2026, 10, 1), range.start)
        assertEquals(at(2026, 11, 1), range.endExclusive)
    }

    @Test
    fun `gregorian december rolls over to january`() {
        val range = CalendarMonth.containing(at(2026, 12, 31, 23) + 59 * minute, jalali = false, zone = tehran)
        assertEquals(at(2026, 12, 1), range.start)
        assertEquals(at(2027, 1, 1), range.endExclusive)
    }

    @Test
    fun `jalali mehr 1405 runs from 23 september to 23 october`() {
        // ۱۴ مهر ۱۴۰۵
        val range = CalendarMonth.containing(at(2026, 10, 6, 14), jalali = true, zone = tehran)
        assertEquals(at(2026, 9, 23), range.start)
        assertEquals(at(2026, 10, 23), range.endExclusive)
    }

    @Test
    fun `jalali esfand rolls over to farvardin of next year`() {
        // ۱۴۰۵ کبیسه نیست، پس اسفندش ۲۹ روز است: ۱ اسفند ۱۴۰۵ تا ۱ فروردین ۱۴۰۶.
        val range = CalendarMonth.containing(at(2027, 3, 1, 9), jalali = true, zone = tehran)
        assertEquals(at(2027, 2, 20), range.start)
        assertEquals(at(2027, 3, 21), range.endExclusive)
    }

    @Test
    fun `the first moment belongs to the month and the next first does not`() {
        val range = CalendarMonth.containing(at(2026, 9, 23), jalali = true, zone = tehran)
        assertTrue(range.start in range)
        assertFalse(range.endExclusive in range)
        assertFalse(range.start - 1 in range)
        assertEquals(at(2026, 9, 23), range.start)
    }

    @Test
    fun `month starts at local midnight, not utc midnight`() {
        // ۰۰:۳۰ اول اکتبر تهران هنوز ۳۰ سپتامبر UTC است.
        val now = at(2026, 10, 1) + 30 * minute
        assertEquals(at(2026, 10, 1), CalendarMonth.containing(now, jalali = false, zone = tehran).start)
        assertEquals(at(2026, 9, 1, zone = utc), CalendarMonth.containing(now, jalali = false, zone = utc).start)
    }
}
