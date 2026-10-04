package ir.asudehapp.sms.persian

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** آنچه گروه‌بندی از هر حباب لازم دارد. */
data class BubbleMeta(val millis: Long, val outgoing: Boolean, val sender: String)

/** روز یک پیامک نسبت به امروز، برای جداکنندهٔ «امروز / دیروز / ۳ مهر». */
enum class DayLabel { TODAY, YESTERDAY, DATE }

/**
 * جداکنندهٔ روز و دسته‌کردن حباب‌های پیاپی (ROADMAP D3). روز با منطقهٔ زمانی
 * گوشی حساب می‌شود، نه UTC؛ نیمه‌شب تهران با نیمه‌شب UTC فرق دارد.
 */
object MessageGrouping {

    /** فاصلهٔ بیشینهٔ دو پیامک یک دسته. */
    const val RUN_GAP_MILLIS: Long = 3 * 60 * 1_000L

    fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean = day(a, zone) == day(b, zone)

    fun label(millis: Long, now: Long, zone: ZoneId): DayLabel {
        val date = day(millis, zone)
        val today = day(now, zone)
        return when (date) {
            today -> DayLabel.TODAY
            today.minusDays(1) -> DayLabel.YESTERDAY
            else -> DayLabel.DATE
        }
    }

    /**
     * [next] دنبالهٔ همان دستهٔ [previous] است: همان طرف، همان فرستنده، همان روز
     * و کمتر از سه دقیقه فاصله.
     */
    fun sameRun(previous: BubbleMeta, next: BubbleMeta, zone: ZoneId): Boolean =
        previous.outgoing == next.outgoing &&
            previous.sender == next.sender &&
            next.millis - previous.millis in 0 until RUN_GAP_MILLIS &&
            sameDay(previous.millis, next.millis, zone)
}
