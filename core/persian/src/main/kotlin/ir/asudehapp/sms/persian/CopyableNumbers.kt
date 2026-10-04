package ir.asudehapp.sms.persian

/** نوع عددی که در متن پیامک تراشهٔ «کپی» می‌گیرد (ROADMAP D1). */
enum class CopyKind { CARD, SHEBA, PHONE }

/**
 * یک عدد قابل کپی در متن. [start] و [end] بازهٔ آن در متن اصلی است؛ [value]
 * همان عدد با ارقام لاتین و بی فاصله و خط تیره است، آمادهٔ چسباندن (ADR-0013).
 */
data class Copyable(val kind: CopyKind, val start: Int, val end: Int, val value: String)

/**
 * پیدا کردن شمارهٔ کارت (۱۶ رقم با Luhn درست)، شبا (`IR` و ۲۴ رقم با mod-97
 * درست) و شمارهٔ تلفن ایرانی در متن پیامک. متن عوض نمی‌شود (D50)؛ فقط بازه‌ها
 * برمی‌گردند. ارقام فارسی، عربی و لاتین، و فاصله یا خط تیره میان گروه‌ها
 * پذیرفته می‌شود.
 */
object CopyableNumbers {

    fun find(body: String): List<Copyable> {
        // `latinDigits` طول متن را عوض نمی‌کند، پس بازه‌ها همان بازه‌های متن اصلی‌اند.
        val text = PersianText.latinDigits(body)
        val found = mutableListOf<Copyable>()
        fun free(range: IntRange) = found.none { range.first < it.end && it.start <= range.last }

        for (match in SHEBA.findAll(text)) {
            val digits = match.value.filter(Char::isDigit)
            if (free(match.range) && shebaValid(digits)) {
                found += Copyable(CopyKind.SHEBA, match.range.first, match.range.last + 1, "IR$digits")
            }
        }
        for (match in CARD.findAll(text)) {
            val digits = match.value.filter(Char::isDigit)
            if (free(match.range) && luhnValid(digits)) {
                found += Copyable(CopyKind.CARD, match.range.first, match.range.last + 1, digits)
            }
        }
        for (match in PHONE.findAll(text)) {
            if (free(match.range)) {
                val value = match.value.filter { it.isDigit() || it == '+' }
                found += Copyable(CopyKind.PHONE, match.range.first, match.range.last + 1, value)
            }
        }
        return found.sortedBy { it.start }
    }

    /** الگوریتم Luhn برای شمارهٔ کارت. */
    fun luhnValid(digits: String): Boolean {
        if (digits.length != CARD_LENGTH || digits.any { !it.isDigit() }) return false
        val sum = digits.reversed().mapIndexed { index, ch ->
            val digit = ch - '0'
            // رقم دوبرابرشده‌ای که دورقمی شود، جمع دو رقمش حساب می‌شود.
            if (index % 2 == 1) (digit * 2).let { it / DECIMAL + it % DECIMAL } else digit
        }.sum()
        return sum % DECIMAL == 0
    }

    /**
     * شبا (IBAN ایران): `IR`، دو رقم کنترل و ۲۲ رقم. چهار نویسهٔ اول به آخر
     * می‌روند، I و R به ۱۸ و ۲۷ تبدیل می‌شوند و باقی‌ماندهٔ تقسیم بر ۹۷ باید ۱ باشد.
     */
    fun shebaValid(digits: String): Boolean {
        if (digits.length != SHEBA_DIGITS || digits.any { !it.isDigit() }) return false
        val rearranged = digits.substring(2) + IR_AS_DIGITS + digits.substring(0, 2)
        var remainder = 0
        for (ch in rearranged) remainder = (remainder * DECIMAL + (ch - '0')) % IBAN_MODULUS
        return remainder == 1
    }

    private const val DECIMAL = 10
    private const val CARD_LENGTH = 16
    private const val SHEBA_DIGITS = 24
    private const val IBAN_MODULUS = 97
    private const val IR_AS_DIGITS = "1827"

    /** چهار گروه چهاررقمی، با فاصله یا خط تیرهٔ اختیاری میانشان. */
    private val CARD = Regex("""(?<![\d])\d{4}(?:[ \-]?\d{4}){3}(?![\d])""")

    /** `IR` و ۲۴ رقم، با فاصلهٔ اختیاری میان گروه‌ها. */
    private val SHEBA = Regex("""(?<![A-Za-z\d])[Ii][Rr](?:[ \-]?\d){24}(?![\d])""")

    /** همراه (۰۹…، ‎+۹۸۹…، ۰۰۹۸۹…) و ثابت با پیش‌شمارهٔ شهر (۰۲۱…). */
    private val PHONE = Regex(
        """(?<![\d+])(?:(?:\+98|0098)[ \-]?|0)9\d{2}[ \-]?\d{3}[ \-]?\d{4}(?![\d])""" +
            """|(?<![\d+])0[1-8]\d[ \-]?\d{8}(?![\d])""",
    )
}
