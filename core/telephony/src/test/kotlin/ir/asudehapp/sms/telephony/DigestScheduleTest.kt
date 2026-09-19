package ir.asudehapp.sms.telephony

import ir.asudehapp.sms.data.DigestFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestScheduleTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, tehran).toInstant().toEpochMilli()

    private fun next(now: Long, frequency: DigestFrequency) =
        DigestSchedule.nextAt(now, frequency, tehran)?.let {
            ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), tehran)
        }

    @Test
    fun `daily digest is at nine tonight, or tomorrow once nine has passed`() {
        assertEquals(at(2026, 9, 19, 21), next(at(2026, 9, 19, 8), DigestFrequency.DAILY)!!.toInstant().toEpochMilli())
        assertEquals(at(2026, 9, 20, 21), next(at(2026, 9, 19, 21), DigestFrequency.DAILY)!!.toInstant().toEpochMilli())
        assertEquals(at(2026, 9, 20, 21), next(at(2026, 9, 19, 22, 30), DigestFrequency.DAILY)!!.toInstant().toEpochMilli())
    }

    @Test
    fun `weekly digest is on friday night`() {
        val result = next(at(2026, 9, 19, 12), DigestFrequency.WEEKLY)!!
        assertEquals(DayOfWeek.FRIDAY, result.dayOfWeek)
        assertEquals(21, result.hour)
        val fridayLate = next(at(2026, 9, 25, 22), DigestFrequency.WEEKLY)!!
        assertEquals(at(2026, 10, 2, 21), fridayLate.toInstant().toEpochMilli())
    }

    @Test
    fun `off means no alarm`() {
        assertNull(DigestSchedule.nextAt(at(2026, 9, 19, 8), DigestFrequency.OFF, tehran))
    }
}
