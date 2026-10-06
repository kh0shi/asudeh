package ir.asudehapp.sms.persian

import java.time.Instant
import java.time.ZoneId

/**
 * خروجی متنی یک گفتگو (PARITY §ب-۹، ROADMAP E9). فایل را کاربر با SAF جایی که
 * خودش می‌خواهد می‌گذارد؛ اپ آن را به جایی نمی‌فرستد.
 *
 * شکل خروجی ساده و خوانا در هر ویرایشگری است: عنوان گفتگو، و برای هر روز یک
 * سرخط تاریخ و زیرش هر پیامک در یک بند با ساعت و فرستنده. خط‌های تازهٔ متن
 * پیامک دست نمی‌خورند، فقط تورفتگی می‌گیرند تا مرز پیامک‌ها گم نشود.
 */
object ConversationExport {

    data class Line(val millis: Long, val sender: String, val text: String)

    fun format(title: String, lines: List<Line>, jalali: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            append(title).append('\n')
            var lastDay: GregorianDate? = null
            for (line in lines.sortedBy { it.millis }) {
                val time = Instant.ofEpochMilli(line.millis).atZone(zone)
                val day = GregorianDate(time.year, time.monthValue, time.dayOfMonth)
                if (day != lastDay) {
                    append('\n').append("— ").append(dayLabel(day, jalali)).append(" —").append('\n')
                    lastDay = day
                }
                append("[%02d:%02d] ".format(time.hour, time.minute))
                append(line.sender).append(": ")
                append(line.text.replace("\n", "\n    ")).append('\n')
            }
        }

    private fun dayLabel(day: GregorianDate, jalali: Boolean): String =
        if (jalali) JalaliDate.of(day).formatShort() else "%04d/%02d/%02d".format(day.year, day.month, day.day)
}
