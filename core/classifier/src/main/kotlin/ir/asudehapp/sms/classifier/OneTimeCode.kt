package ir.asudehapp.sms.classifier

/**
 * «آیا چیزی در این متن **شکلِ** رمز یکبار مصرف را دارد؟»
 *
 * رمز یکبار مصرف ایرانی کاملاً عددی است، ولی کد تخفیف تقریباً همیشه حرف دارد
 * («کد: QXB8C7»). همین تفاوتِ شکلی، بدون هیچ کلیدواژهٔ دیگری، پیامکِ
 *
 *     بانک سامان / خريد / پاسچی / مبلغ 592,250 ريال / رمز 081771
 *
 * را از تبلیغ جدا می‌کند. چنین پیامکی پیش از این، چون «خريد» داشت، `Promo`
 * شمرده می‌شد و اعلان بی‌صدا می‌گرفت — بدترین حالت ممکن برای رمز پویا.
 *
 * این تشخیص است، نه استخراج؛ بیرون کشیدن رمز برای دکمهٔ «کپی رمز» کار
 * [OtpCode] است.
 */
object OneTimeCode {

    /** واژه‌ای که رمز پس از آن می‌آید. روی متن نرمال‌شده (D50) مقایسه می‌شود. */
    private val LABEL =
        Regex("""(?<![\p{L}\p{N}])(?:رمز|کد|پین|code|pin|otp|password)(?![\p{L}\p{N}])""")

    /** فاصله و نشانه‌هایی که میان واژه و خودِ رمز می‌آیند: «کد: ۱۲۳۴»، «کد ورود ۱۲۳۴». */
    private val SEPARATOR = Regex("""[\s:：=\-–—#.،,]+""")

    private val WORD = Regex("""[\p{L}\p{N}]+""")

    /**
     * فاصلهٔ بیشینه از واژه تا رمز، به نویسه. «کد ورود به حساب کاربری ایرانسل
     * من: ۲۵۷۵» درست سر همین اندازه است.
     */
    private const val MAX_DISTANCE = 40

    /**
     * رمزِ یکبارمصرفِ درون [normalizedText]، یا `null`.
     *
     * [excluded] واژه‌هایی‌اند که یعنی کدِ پس از آن‌ها یکبارمصرف نیست: «کد
     * تخفیف»، «کد پیگیری»، «کد ملی». باید نرمال‌شده باشند.
     */
    fun find(normalizedText: String, excluded: List<String>, lengths: IntRange = 4..8): String? {
        for (label in LABEL.findAll(normalizedText)) {
            codeAfter(normalizedText, label.range.last + 1, excluded, lengths)?.let { return it }
        }
        return null
    }

    /**
     * از [start] به بعد واژه‌ها را یکی‌یکی می‌خواند تا به رمز برسد. واژهٔ ساده
     * («ورود»، «تایید»، «شما») رد می‌شود؛ ولی عددی که طولش به رمز نمی‌خورد،
     * توکنی که حرف و رقم را قاطی کرده، و واژهٔ [excluded] زنجیره را می‌شکنند.
     */
    private fun codeAfter(text: String, start: Int, excluded: List<String>, lengths: IntRange): String? {
        var at = start
        val limit = minOf(text.length, start + MAX_DISTANCE)
        // درست پس از «کد:» خودِ رمز می‌آید و نه واژهٔ دیگری؛ «کد: milka» رمز نیست.
        var afterColon = false
        while (at < limit) {
            SEPARATOR.matchAt(text, at)?.let {
                afterColon = it.value.contains(':') || it.value.contains('：')
                at = it.range.last + 1
            }
            val word = WORD.matchAt(text, at) ?: return null
            val value = word.value
            when {
                value.all { it.isDigit() } -> return value.takeIf { it.length in lengths }
                value.any { it.isDigit() } -> return null
                value in excluded -> return null
                afterColon -> return null
                else -> at = word.range.last + 1
            }
        }
        return null
    }
}
