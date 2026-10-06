package ir.asudehapp.sms.persian

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * بازهٔ زمانی یک ماه تقویمی: از [start] (شامل) تا [endExclusive] (بیرون)، به
 * میلی‌ثانیه. برای «آمار آرامش» (ADR-0019).
 */
data class MonthRange(val start: Long, val endExclusive: Long) {
    operator fun contains(millis: Long): Boolean = millis >= start && millis < endExclusive
}

object CalendarMonth {

    /**
     * ماهی که [now] در آن است، شمسی یا میلادی مثل رابط، از نیمه‌شب روز اول در
     * منطقهٔ زمانی [zone] تا نیمه‌شب روز اول ماه بعد.
     */
    fun containing(now: Long, jalali: Boolean, zone: ZoneId = ZoneId.systemDefault()): MonthRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val (first, next) = if (jalali) jalaliBounds(today) else gregorianBounds(today)
        return MonthRange(startOf(first, zone), startOf(next, zone))
    }

    private fun gregorianBounds(today: LocalDate): Pair<LocalDate, LocalDate> {
        val first = today.withDayOfMonth(1)
        return first to first.plusMonths(1)
    }

    private fun jalaliBounds(today: LocalDate): Pair<LocalDate, LocalDate> {
        val shamsi = JalaliDate.of(today.year, today.monthValue, today.dayOfMonth)
        val first = JalaliDate(shamsi.year, shamsi.month, 1)
        val next = if (shamsi.month == LAST_MONTH) JalaliDate(shamsi.year + 1, 1, 1) else JalaliDate(shamsi.year, shamsi.month + 1, 1)
        return first.toLocalDate() to next.toLocalDate()
    }

    private fun JalaliDate.toLocalDate(): LocalDate = toGregorian().let { LocalDate.of(it.year, it.month, it.day) }

    private fun startOf(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private const val LAST_MONTH = 12
}
