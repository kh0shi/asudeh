package ir.asudehapp.sms.persian

/**
 * متن جستجو (FTS، D20). این متن فقط در ستون جستجو ذخیره می‌شود و هرگز به
 * کاربر نشان داده نمی‌شود؛ متن پیامک دست نمی‌خورد (D50).
 *
 * توکن‌ساز `simple` در FTS4 هر نویسهٔ غیرلاتین را جزو کلمه می‌شمارد، پس «،» یا
 * «»» به کلمهٔ کناری می‌چسبد. برای همین اینجا هر چیزی جز حرف و رقم به فاصله
 * تبدیل می‌شود، و نیم‌فاصله و ی/ي و ارقام فارسی با [PersianText.normalize]
 * یکی می‌شوند.
 */
object SearchText {

    /** متن قابل جستجوی یک پیامک: متن و سرشماره، کنار هم. */
    fun of(vararg parts: String): String = parts
        .joinToString(" ") { tokens(it).joinToString(" ") }
        .trim()

    /**
     * عبارت `MATCH` برای پرسش کاربر. هر کلمه با پیشوند تطبیق داده می‌شود
     * («تخف» پیامک «تخفیف» را هم پیدا می‌کند) و همهٔ کلمه‌ها باید باشند. خروجی
     * `null` یعنی پرسش چیزی برای جستجو ندارد.
     */
    fun ftsQuery(userQuery: String): String? {
        val words = tokens(userQuery)
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }

    /** کلمه‌های نرمال‌شده؛ فقط حرف و رقم. */
    fun tokens(text: String): List<String> {
        val normalized = PersianText.normalize(text)
        val builder = StringBuilder(normalized.length)
        for (ch in normalized) {
            builder.append(if (ch.isLetterOrDigit()) ch else ' ')
        }
        return builder.split(' ').filter { it.isNotEmpty() }
    }
}
