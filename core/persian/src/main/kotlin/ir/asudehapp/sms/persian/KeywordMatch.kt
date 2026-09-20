package ir.asudehapp.sms.persian

/**
 * تطبیق کلیدواژه‌های «قواعد من» با متن پیامک (ADR-0012).
 *
 * قاعدهٔ تطبیق را خود کاربر خواسته است: حرف بزرگ و کوچک، ارقام فارسی و
 * انگلیسی، فاصله و نیم‌فاصله هیچ‌کدام فرقی نمی‌کنند. پس هر دو طرفِ مقایسه اول
 * با [PersianText.normalize] یکسان می‌شوند و بعد **همهٔ فاصله‌ها برداشته
 * می‌شوند**؛ این‌طور «لغو ۱۱» و «لغو۱۱» و «لغو 11» یک چیزند.
 *
 * چون فاصله‌ای نمی‌ماند، مرز کلمه هم نمی‌ماند: کلیدواژه هر جای متن که بیاید
 * پیدا می‌شود، حتی داخل یک کلمهٔ بلندتر. این بهای همان خواستهٔ کاربر است و
 * عمدی است.
 *
 * یک جانشین هم هست: `{لینک}` یعنی «یک نشانی اینترنتی»، تا قاعده‌ای مثل
 * «لغو{لینک}» بشود نوشت. متن پیش و پس از جانشین باید به همان ترتیب بیاید و
 * فاصله‌شان تا خود لینک از [MAX_GAP] نویسه بیشتر نباشد.
 */
object KeywordMatch {

    /** جانشین «یک نشانی اینترنتی» داخل کلیدواژه. */
    const val LINK_PLACEHOLDER: String = "{لینک}"

    /** بیشترین نویسهٔ بی‌ربطی که بین دو تکهٔ یک کلیدواژه پذیرفته می‌شود. */
    const val MAX_GAP: Int = 12

    /**
     * یک نشانی اینترنتی در متنِ بی‌فاصله. حروف لاتین‌اند، پس با حرف فارسی
     * اطرافش قاطی نمی‌شود.
     */
    private const val LINK = """(?:https?://)?[a-z0-9][a-z0-9._-]*\.[a-z]{2,}(?:/[a-z0-9._~:/?#\[\]@!$&'()*+,;=%-]*)?"""

    /** متن آمادهٔ مقایسه: نرمال‌شده و بدون هیچ فاصله‌ای. */
    fun key(text: String): String =
        PersianText.normalize(text).filterNot(Char::isWhitespace)

    /**
     * کلیدواژه را به یک الگو تبدیل می‌کند. خروجی `null` یعنی کلیدواژه بعد از
     * نرمال‌سازی چیزی برای جستجو ندارد (مثلاً فقط فاصله بوده است).
     */
    fun compile(keyword: String): Regex? {
        val parts = keyword.split(LINK_PLACEHOLDER)
        if (parts.size == 1) {
            val key = key(keyword)
            return if (key.isEmpty()) null else Regex(Regex.escape(key))
        }
        val pattern = buildString {
            for ((index, part) in parts.withIndex()) {
                if (index > 0) {
                    // بین تکهٔ قبلی و لینک، کمی متن بی‌ربط پذیرفته می‌شود.
                    append(".{0,").append(MAX_GAP).append("}?").append(LINK)
                }
                val key = key(part)
                if (key.isNotEmpty()) append(Regex.escape(key))
            }
        }
        return runCatching { Regex(pattern) }.getOrNull()
    }
}
