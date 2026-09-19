package ir.asudehapp.sms.persian

/**
 * تقویم شمسی (D50).
 *
 * `android.icu` تقویم شمسی را روی همهٔ نسخه‌های هدف تضمین نمی‌کند، پس تبدیل را
 * خودمان انجام می‌دهیم. الگوریتم همان الگوریتم شناخته‌شدهٔ «جلالی» با جدول
 * `breaks` است که برای سال‌های ۱۱۷۸ تا ۱۶۳۳ شمسی دقیق است و بازهٔ ۱۳۰۰ تا ۱۵۰۰
 * خواستهٔ D50 را در بر می‌گیرد.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    init {
        require(month in 1..12) { "ماه شمسی باید بین ۱ و ۱۲ باشد: $month" }
        require(day in 1..31) { "روز شمسی باید بین ۱ و ۳۱ باشد: $day" }
    }

    val monthName: String get() = MONTH_NAMES[month - 1]

    /** مثلاً «۲۷ شهریور ۱۴۰۵». */
    fun formatLong(): String =
        PersianText.persianDigits("$day $monthName $year")

    /** مثلاً «۱۴۰۵/۰۶/۲۷». */
    fun formatShort(): String =
        PersianText.persianDigits(
            "%04d/%02d/%02d".format(year, month, day)
        )

    fun toGregorian(): GregorianDate = jdnToGregorian(toJdn())

    fun toJdn(): Int = jalaliToJdn(year, month, day)

    companion object {

        val MONTH_NAMES: List<String> = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        val WEEKDAY_NAMES: List<String> = listOf(
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
        )

        fun of(gregorian: GregorianDate): JalaliDate =
            jdnToJalali(gregorianToJdn(gregorian.year, gregorian.month, gregorian.day))

        fun of(year: Int, month: Int, day: Int): JalaliDate =
            jdnToJalali(gregorianToJdn(year, month, day))

        /** نام روز هفته برای این تاریخ. شنبه اولین روز است. */
        fun weekdayName(date: JalaliDate): String =
            WEEKDAY_NAMES[((date.toJdn() + 2) % 7 + 7) % 7]
    }
}

data class GregorianDate(val year: Int, val month: Int, val day: Int)

/** سال‌هایی که طول چرخهٔ کبیسه در آن‌ها عوض می‌شود. */
private val BREAKS = intArrayOf(
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
)

private class JalaliCal(val leap: Int, val gy: Int, val march: Int)

private fun jalCal(jy: Int): JalaliCal {
    val gy = jy + 621
    var leapJ = -14
    var jp = BREAKS[0]
    require(jy >= jp && jy < BREAKS[BREAKS.size - 1]) { "سال شمسی خارج از بازهٔ پشتیبانی‌شده: $jy" }

    var jump = 0
    for (i in 1 until BREAKS.size) {
        val jm = BREAKS[i]
        jump = jm - jp
        if (jy < jm) break
        leapJ += (jump / 33) * 8 + (jump % 33) / 4
        jp = jm
    }
    var n = jy - jp
    leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
    if (jump % 33 == 4 && jump - n == 4) leapJ += 1

    val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
    val march = 20 + leapJ - leapG

    if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
    var leap = ((n + 1) % 33 - 1) % 4
    if (leap == -1) leap = 4

    return JalaliCal(leap, gy, march)
}

/** طول ماه شمسی، با در نظر گرفتن کبیسه برای اسفند. */
fun jalaliMonthLength(year: Int, month: Int): Int = when {
    month <= 6 -> 31
    month <= 11 -> 30
    isJalaliLeapYear(year) -> 30
    else -> 29
}

/**
 * `leap` شمار سال‌های گذشته از آخرین کبیسه است، پس مقدار صفر یعنی خودِ همین سال
 * کبیسه است.
 */
fun isJalaliLeapYear(year: Int): Boolean = jalCal(year).leap == 0

private fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
    val r = jalCal(jy)
    return gregorianToJdn(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
}

private fun jdnToJalali(jdn: Int): JalaliDate {
    val gregorian = jdnToGregorian(jdn)
    var jy = gregorian.year - 621
    val r = jalCal(jy)
    val firstFarvardin = gregorianToJdn(r.gy, 3, r.march)
    var k = jdn - firstFarvardin
    if (k >= 0) {
        if (k <= 185) {
            return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
        }
        k -= 186
    } else {
        jy -= 1
        k += 179
        if (r.leap == 1) k += 1
    }
    return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
}

fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
    var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 +
        (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
    d -= (((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 - 752
    return d
}

fun jdnToGregorian(jdn: Int): GregorianDate {
    var j = 4 * jdn + 139361631
    j += ((((4 * jdn + 183187720) / 146097) * 3) / 4) * 4 - 3908
    val i = ((j % 1461) / 4) * 5 + 308
    val gd = (i % 153) / 5 + 1
    val gm = ((i / 153) % 12) + 1
    val gy = j / 1461 - 100100 + (8 - gm) / 6
    return GregorianDate(gy, gm, gd)
}
