package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.persian.PersianText

/**
 * بیرون کشیدن رمز یکبار از متن پیامک، برای دکمهٔ «کپی رمز» در اعلان (D38، D41).
 *
 * فقط پیامکی که `Classifier` آن را `OTP` دانسته به اینجا می‌رسد. اگر معلوم
 * نباشد کدام عدد رمز است، `null` برمی‌گردد و دکمه نشان داده نمی‌شود؛ کپی
 * کردن عدد اشتباه (مثلاً مبلغ یا شمارهٔ کارت) بدتر از نبودن دکمه است.
 */
object OtpCode {

    private val CANDIDATE = Regex("""(?<![\d.,/:\-])\d{4,8}(?![\d.,/:\-])""")

    /** واژه‌هایی که درست پیش از رمز می‌آیند. */
    private val KEYWORD = Regex("""(رمز|کد|code|otp|pin|password|پین)""")

    /** عددی که پس از این‌ها بیاید مبلغ است، نه رمز. */
    private val AMOUNT_UNIT = Regex("""^\s*(ریال|تومان|rial|irr)""")

    fun extract(body: String): String? {
        val text = PersianText.latinDigits(body).lowercase()
        val candidates = CANDIDATE.findAll(text)
            .filterNot { AMOUNT_UNIT.containsMatchIn(text.substring(it.range.last + 1)) }
            .toList()
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single().value

        // چند عدد: نزدیک‌ترین عدد پس از واژهٔ «رمز» یا «کد».
        val keywords = KEYWORD.findAll(text).map { it.range.last }.toList()
        if (keywords.isEmpty()) return null
        return candidates
            .mapNotNull { candidate ->
                val distance = keywords
                    .filter { it < candidate.range.first }
                    .minOfOrNull { candidate.range.first - it }
                distance?.let { candidate.value to it }
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= MAX_DISTANCE }
            ?.first
    }

    private const val MAX_DISTANCE = 40
}
