package ir.asudehapp.sms.persian

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** یکی از زمان‌های پیشنهادی برای ارسال زمان‌بندی‌شده. */
enum class SchedulePreset {
    /** امشب، ساعت ۲۱. */
    TONIGHT,

    /** فردا صبح، ساعت ۸. */
    TOMORROW_MORNING,

    /** فردا بعدازظهر، ساعت ۱۴. */
    TOMORROW_AFTERNOON,
}

/** یک پیشنهاد آماده، با زمان دقیقش. */
data class ScheduleChoice(val preset: SchedulePreset, val atMillis: Long)

/** فاصلهٔ زمان ارسال تا حالا، برای ساختن متن «کی فرستاده می‌شود». */
enum class ScheduleDay { TODAY, TOMORROW, LATER }

/**
 * زمان ارسال، تکه‌تکه، تا رابط بتواند با متن‌های `strings.xml` جمله بسازد.
 * تاریخ شمسی است، چون رابط شمسی است (D50).
 */
data class ScheduleWhen(
    val day: ScheduleDay,
    val hour: Int,
    val minute: Int,
    val date: JalaliDate,
) {
    /** «۰۹:۰۵»، با ارقام لاتین؛ ارقام فارسی کار رابط است. */
    fun clock(): String = "%02d:%02d".format(hour, minute)
}

/**
 * ارسال زمان‌بندی‌شده (ADR-0011).
 *
 * همهٔ حساب‌های زمانی اینجا و خالص‌اند، تا بدون اندروید آزمون شوند: خود ارسال
 * فقط یک هشدار `AlarmManager` است و این تابع‌ها می‌گویند آن هشدار برای کِی
 * باشد و وقتی دیر رسید چه باید کرد.
 */
object SendSchedule {

    /** پیامک زمان‌بندی‌شده دست‌کم یک دقیقه بعد از «حالا» فرستاده می‌شود. */
    const val MIN_LEAD_MILLIS: Long = 60_000L

    /**
     * اگر گوشی خاموش بوده و زمان ارسال بیشتر از این گذشته باشد، پیامک **بی‌صدا
     * فرستاده نمی‌شود**: کاربر می‌بیند که نرفته و خودش تصمیم می‌گیرد. فرستادن
     * پیامک دیروز بدون اطلاع کاربر، خودش یک غافلگیری است (اصل ۸).
     */
    const val LATE_GRACE_MILLIS: Long = 6 * 60 * 60 * 1_000L

    const val TONIGHT_HOUR: Int = 21
    const val MORNING_HOUR: Int = 8
    const val AFTERNOON_HOUR: Int = 14

    /**
     * پیشنهادهای آماده برای [nowMillis]. پیشنهادی که دیگر معنی ندارد (مثلاً
     * «امشب» در ساعت ۲۳) در فهرست نیست، پس فهرست می‌تواند کوتاه‌تر شود.
     */
    fun presets(nowMillis: Long, zone: ZoneId): List<ScheduleChoice> {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val tonight = now.atHour(TONIGHT_HOUR)
        val choices = mutableListOf<ScheduleChoice>()
        if (tonight.isUsableAfter(now)) {
            choices += ScheduleChoice(SchedulePreset.TONIGHT, tonight.millis())
        }
        choices += ScheduleChoice(SchedulePreset.TOMORROW_MORNING, now.plusDays(1).atHour(MORNING_HOUR).millis())
        choices += ScheduleChoice(SchedulePreset.TOMORROW_AFTERNOON, now.plusDays(1).atHour(AFTERNOON_HOUR).millis())
        return choices
    }

    /**
     * زمانی که کاربر خودش انتخاب کرده است. خروجی `null` یعنی زمان گذشته یا
     * آن‌قدر نزدیک است که ارسال فوری معنی‌دارتر است؛ رابط در این حالت زمان‌بندی
     * را نمی‌پذیرد.
     */
    fun validate(atMillis: Long, nowMillis: Long): Long? =
        atMillis.takeIf { it - nowMillis >= MIN_LEAD_MILLIS }

    /** زمان ارسال رسیده است. */
    fun isDue(atMillis: Long, nowMillis: Long): Boolean = nowMillis >= atMillis

    /**
     * آن‌قدر دیر شده که ارسال خودکار درست نیست (مثلاً گوشی تا فردا خاموش بوده).
     */
    fun isTooLate(atMillis: Long, nowMillis: Long): Boolean =
        nowMillis - atMillis > LATE_GRACE_MILLIS

    /** روز و ساعت ارسال، برای ساختن متن در رابط. */
    fun describe(atMillis: Long, nowMillis: Long, zone: ZoneId): ScheduleWhen {
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val days = at.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        val day = when (days) {
            0L -> ScheduleDay.TODAY
            1L -> ScheduleDay.TOMORROW
            else -> ScheduleDay.LATER
        }
        return ScheduleWhen(
            day = day,
            hour = at.hour,
            minute = at.minute,
            date = JalaliDate.of(at.year, at.monthValue, at.dayOfMonth),
        )
    }

    /**
     * لحظهٔ ارسال از روی روز و ساعتی که کاربر انتخاب کرده است. روز به‌صورت
     * «روز از مبدأ» گرفته می‌شود تا رابط بتواند خروجی تقویم را مستقیم بدهد.
     */
    fun at(zone: ZoneId, epochDay: Long, hour: Int, minute: Int): Long =
        LocalDate.ofEpochDay(epochDay)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * تقویم Material زمان را به‌صورت نیمه‌شبِ UTC می‌دهد؛ این تابع آن را به
     * «روز از مبدأ» تبدیل می‌کند تا با [at] جفت شود.
     */
    fun epochDayOfUtcMillis(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    /** «روز از مبدأ» برای امروز، تا تقویم از امروز شروع شود. */
    fun today(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    private fun ZonedDateTime.atHour(hour: Int): ZonedDateTime =
        withHour(hour).withMinute(0).withSecond(0).withNano(0)

    private fun ZonedDateTime.isUsableAfter(now: ZonedDateTime): Boolean =
        toInstant().toEpochMilli() - now.toInstant().toEpochMilli() >= MIN_LEAD_MILLIS

    private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()
}
