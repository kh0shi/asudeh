package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliDateTest {

    @Test
    fun `converts known gregorian dates`() {
        assertEquals(JalaliDate(1405, 6, 27), JalaliDate.of(2026, 9, 18))
        assertEquals(JalaliDate(1399, 1, 1), JalaliDate.of(2020, 3, 20))
        assertEquals(JalaliDate(1403, 10, 12), JalaliDate.of(2025, 1, 1))
        assertEquals(JalaliDate(1300, 1, 1), JalaliDate.of(1921, 3, 21))
    }

    @Test
    fun `round trips every day between 1300 and 1500`() {
        var checked = 0
        for (year in 1300..1500) {
            for (month in 1..12) {
                for (day in 1..jalaliMonthLength(year, month)) {
                    val jalali = JalaliDate(year, month, day)
                    val back = JalaliDate.of(jalali.toGregorian())
                    assertEquals(jalali, back)
                    checked++
                }
            }
        }
        assertTrue("تعداد روزهای بررسی‌شده کم است: $checked", checked > 73_000)
    }

    @Test
    fun `leap years have a 30 day esfand`() {
        assertTrue(isJalaliLeapYear(1399))
        assertTrue(isJalaliLeapYear(1403))
        assertFalse(isJalaliLeapYear(1404))
        assertEquals(30, jalaliMonthLength(1403, 12))
        assertEquals(29, jalaliMonthLength(1404, 12))
    }

    @Test
    fun `formats with persian digits`() {
        assertEquals("۲۷ شهریور ۱۴۰۵", JalaliDate(1405, 6, 27).formatLong())
        assertEquals("۱۴۰۵/۰۶/۲۷", JalaliDate(1405, 6, 27).formatShort())
    }

    @Test
    fun `weekday names start at saturday`() {
        assertEquals("جمعه", JalaliDate.weekdayName(JalaliDate.of(2026, 9, 18)))
    }
}
