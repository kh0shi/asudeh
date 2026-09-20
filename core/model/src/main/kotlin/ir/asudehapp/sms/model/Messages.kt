package ir.asudehapp.sms.model

import ir.asudehapp.sms.persian.KeywordMatch

/** ورودی `Classifier`. عمداً هیچ چیزی از قواعد کاربر در آن نیست (D29). */
data class MessageInput(
    val address: String,
    val body: String,
    /** فرستنده در مخاطب‌های گوشی هست. برای قفل ایمنی D31 استفاده می‌شود. */
    val isKnownContact: Boolean = false,
)

/**
 * قاعده‌های خودِ آسوده که در «قواعد من» دیده می‌شوند و کاربر می‌تواند
 * خاموششان کند (ADR-0012). هیچ‌کدام پیامکی را پاک نمی‌کنند؛ فقط جای پیامک را
 * تعیین می‌کنند، پس خاموش کردنشان هم چیزی را از بین نمی‌برد.
 */
enum class DefaultRule {
    /** مخاطب‌های گوشی به‌طور پیش‌فرض در فهرست سفیدند (D31). */
    CONTACTS_IN_ALLOWLIST,

    /** پیامک شمارهٔ موبایل هرگز خودکار پنهان نمی‌شود (D31). */
    MOBILE_NEVER_HIDDEN,

    /** رمز یکبار و پیامک بانکی همیشه در صندوق می‌مانند (ADR-0006 بند ۲). */
    OTP_AND_BANK_ALWAYS_INBOX,

    /** تبلیغ قطعیِ زنده خودکار به پوشهٔ تبلیغات می‌رود (D30). */
    HIDE_PROMO,

    /** پیامک فیشینگ با شاهد قطعی به پوشهٔ کلاهبرداری می‌رود (اصل ۴). */
    HIDE_SCAM,
    ;

    companion object {
        /** خاموش کردن این‌ها هشدار دارد، چون کاربر را بی‌پناه‌تر می‌کند. */
        val RISKY: Set<DefaultRule> = setOf(HIDE_SCAM, OTP_AND_BANK_ALWAYS_INBOX)
    }
}

/**
 * کلیدواژه‌های پیش‌فرض آسوده (ADR-0012). این‌ها در «قواعد من» کنار قاعده‌های
 * خود کاربر دیده می‌شوند و هرکدام جداگانه خاموش‌شدنی است؛ پاک‌شدنی نیستند، چون
 * با به‌روزرسانی اپ ممکن است عوض شوند.
 */
object DefaultRules {

    /** پیامکی که این‌ها را دارد همیشه در صندوق می‌ماند. */
    val ALLOW_KEYWORDS: List<String> = listOf("کد ورود", "Code")

    /** پیامکی که این‌ها را دارد تبلیغ شمرده می‌شود. */
    val BLOCK_KEYWORDS: List<String> = listOf("تخفیف", "لغو ۱۱", "لغو{لینک}")

    /** شناسهٔ پایدار یک کلیدواژهٔ پیش‌فرض، برای نگه داشتن «خاموش است». */
    fun allowKeywordId(keyword: String): String = "ALLOW|$keyword"

    fun blockKeywordId(keyword: String): String = "BLOCK|$keyword"
}

/**
 * قواعد کاربر: فرستنده‌ها، کلیدواژه‌ها، و قاعده‌های پیش‌فرضی که خاموش شده‌اند.
 *
 * کلیدواژه‌های پیش‌فرضِ خاموش‌نشده پیش از ساختن این شیء به [allowKeywords] و
 * [blockKeywords] اضافه می‌شوند، تا `Router` فرقی بین قاعدهٔ پیش‌فرض و قاعدهٔ
 * کاربر نگذارد.
 * — GLOSSARY: Allowlist، Blocklist
 */
data class UserRules(
    val allowlist: Set<String> = emptySet(),
    val blocklist: Set<String> = emptySet(),
    /** کلیدواژه‌هایی که پیامک حاویشان همیشه در صندوق می‌ماند. */
    val allowKeywords: Set<String> = emptySet(),
    /** کلیدواژه‌هایی که پیامک حاویشان تبلیغ شمرده می‌شود. */
    val blockKeywords: Set<String> = emptySet(),
    val disabledDefaults: Set<DefaultRule> = emptySet(),
) {
    private val normalizedAllow: Set<String> = allowlist.mapTo(mutableSetOf(), Addresses::normalize)
    private val normalizedBlock: Set<String> = blocklist.mapTo(mutableSetOf(), Addresses::normalize)

    /** الگوی هر کلیدواژه، کنار متن خامش برای نمایش در «چرا اینجاست؟». */
    private val allowPatterns: List<Pair<Regex, String>> = compile(allowKeywords)
    private val blockPatterns: List<Pair<Regex, String>> = compile(blockKeywords)

    /** بدون کلیدواژه، `Router` اصلاً متن را آماده نمی‌کند. */
    val hasKeywords: Boolean = allowPatterns.isNotEmpty() || blockPatterns.isNotEmpty()

    fun isAllowed(address: String): Boolean = Addresses.normalize(address) in normalizedAllow
    fun isBlocked(address: String): Boolean = Addresses.normalize(address) in normalizedBlock

    /** قاعدهٔ پیش‌فرضی که کاربر خاموشش نکرده است. */
    fun isOn(rule: DefaultRule): Boolean = rule !in disabledDefaults

    /**
     * اولین کلیدواژهٔ سفیدی که در متن آمده است، برای ساختن `Reason`.
     * [keyedBody] باید با `KeywordMatch.key` آماده شده باشد.
     */
    fun allowKeywordIn(keyedBody: String): String? = firstIn(allowPatterns, keyedBody)

    fun blockKeywordIn(keyedBody: String): String? = firstIn(blockPatterns, keyedBody)

    private fun firstIn(patterns: List<Pair<Regex, String>>, keyedBody: String): String? {
        if (keyedBody.isEmpty()) return null
        // خروجی همان چیزی است که کاربر نوشته، نه شکل نرمال‌شده‌اش.
        return patterns.firstOrNull { it.first.containsMatchIn(keyedBody) }?.second
    }

    companion object {
        val EMPTY: UserRules = UserRules()

        private fun compile(keywords: Set<String>): List<Pair<Regex, String>> =
            keywords.mapNotNull { keyword -> KeywordMatch.compile(keyword)?.let { it to keyword } }
    }
}

/** یکسان‌سازی سرشماره‌ها، تا `+989…` و `989…` و `09…` یک چیز شمرده شوند. */
object Addresses {

    private val AD_LINE_PREFIXES = listOf("1000", "2000", "3000", "5000", "9000")

    fun normalize(address: String): String {
        val digitsOnly = buildString {
            for (ch in address.trim()) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '+' && isEmpty() -> append(ch)
                    ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> Unit
                    else -> append(ch)
                }
            }
        }
        if (digitsOnly.isEmpty()) return address.trim()
        if (digitsOnly.any { !it.isDigit() && it != '+' }) return digitsOnly.lowercase()
        val bare = digitsOnly.removePrefix("+")
        return when {
            bare.startsWith("0098") -> "0" + bare.removePrefix("0098")
            bare.startsWith("98") && bare.length >= 12 -> "0" + bare.removePrefix("98")
            else -> bare
        }
    }

    fun kindOf(address: String): SenderKind {
        val normalized = normalize(address)
        if (normalized.isEmpty()) return SenderKind.ALPHANUMERIC
        if (normalized.any { !it.isDigit() && it != '+' }) return SenderKind.ALPHANUMERIC
        val digits = normalized.removePrefix("+")
        return when {
            digits.startsWith("09") && digits.length == 11 -> SenderKind.MOBILE
            AD_LINE_PREFIXES.any { digits.startsWith(it) } -> SenderKind.AD_LINE
            digits.length in 3..9 -> SenderKind.SHORT_CODE
            else -> SenderKind.OTHER_NUMBER
        }
    }

    /** خطوط انبوه ایرانی؛ فقط برای این‌ها `Unsub11` پیشنهاد می‌شود (D28). */
    fun isAdLine(address: String): Boolean = kindOf(address) == SenderKind.AD_LINE
}
