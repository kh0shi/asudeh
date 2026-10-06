package ir.asudehapp.sms.model

/**
 * پاسخ‌های آماده‌ای که فقط خود کاربر می‌نویسد (ADR-0017).
 *
 * آسوده هیچ متن آماده‌ای با خودش نمی‌آورد: اینجا فقط قاعده‌های نگه‌داشتن
 * متن کاربر است (تمیز کردن، یکتایی، سقف تعداد و طول) و جا دادن یک پاسخ در
 * کادر نوشتن. هیچ چیزی در این شیء چیزی نمی‌فرستد.
 *
 * فهرست همیشه به ترتیبی است که کاربر چیده، و هر تابع یک فهرست تازه برمی‌گرداند؛
 * اگر تغییر پذیرفتنی نباشد، همان فهرست قبلی.
 */
object QuickReplies {

    /** منوی صفحهٔ گفتگو با بیست ردیف هنوز قابل دیدن است. */
    const val MAX_COUNT: Int = 20

    /** برای جمله‌های تکراری روزمره است، نه آرشیو متن. */
    const val MAX_LENGTH: Int = 500

    /** چرا یک متن پاسخ آماده نمی‌شود؛ پنجرهٔ نوشتن همین را نشان می‌دهد. */
    enum class Problem { BLANK, TOO_LONG, DUPLICATE, FULL }

    /** متن کادر نوشتن پس از جا دادن یک پاسخ، و جای تازهٔ مکان‌نما. */
    data class Insertion(val text: String, val cursor: Int)

    /** شکل ذخیره‌شدهٔ یک پاسخ؛ یا `null` اگر خالی یا بلندتر از [MAX_LENGTH] است. */
    fun clean(text: String): String? {
        val cleaned = text.replace("\r\n", "\n").trim()
        return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
    }

    /**
     * ایراد این متن به‌عنوان پاسخ تازه، یا به‌جای [replacing] اگر ویرایش است؛
     * `null` یعنی پذیرفتنی است.
     */
    fun problem(replies: List<String>, text: String, replacing: String? = null): Problem? {
        val cleaned = text.replace("\r\n", "\n").trim()
        return when {
            cleaned.isEmpty() -> Problem.BLANK
            cleaned.length > MAX_LENGTH -> Problem.TOO_LONG
            cleaned != replacing && cleaned in replies -> Problem.DUPLICATE
            replacing == null && replies.size >= MAX_COUNT -> Problem.FULL
            else -> null
        }
    }

    /** افزودن به آخر فهرست. */
    fun add(replies: List<String>, text: String): List<String> {
        if (problem(replies, text) != null) return replies
        return replies + requireNotNull(clean(text))
    }

    /** عوض کردن متن یک پاسخ، در همان جای فهرست. */
    fun edit(replies: List<String>, old: String, text: String): List<String> {
        if (old !in replies || problem(replies, text, replacing = old) != null) return replies
        val cleaned = requireNotNull(clean(text))
        return replies.map { if (it == old) cleaned else it }
    }

    fun remove(replies: List<String>, text: String): List<String> = replies.filterNot { it == text }

    /** جابه‌جا کردن یک پاسخ به اندازهٔ [offset] (منفی یعنی بالاتر)، تا مرز فهرست. */
    fun move(replies: List<String>, text: String, offset: Int): List<String> {
        val from = replies.indexOf(text)
        val to = (from + offset).coerceIn(0, replies.lastIndex.coerceAtLeast(0))
        if (from < 0 || to == from) return replies
        return replies.toMutableList().apply {
            removeAt(from)
            add(to, text)
        }
    }

    /**
     * فهرستی که از هر جا آمده (پایگاه داده، فایل پشتیبان) به شکل درست: تمیز،
     * بدون تکرار (اولی می‌ماند) و تا [MAX_COUNT].
     */
    fun sanitize(replies: List<String>): List<String> =
        replies.mapNotNull(::clean).distinct().take(MAX_COUNT)

    /**
     * بازگردانی از پشتیبان: پاسخ‌های همین گوشی سر جایشان می‌مانند و پاسخ‌های
     * تازهٔ فایل، تا سقف، به آخر اضافه می‌شوند.
     */
    fun merge(existing: List<String>, incoming: List<String>): List<String> = sanitize(existing + incoming)

    /**
     * جا دادن [reply] به‌جای بخش انتخاب‌شدهٔ [text] (یا جای مکان‌نما). اگر به
     * نویسهٔ کناری بچسبد، یک فاصله میانشان می‌آید. مکان‌نما بعد از پاسخ می‌نشیند.
     * برای افزودن به آخر متن، هر دو سر انتخاب را `text.length` بدهید.
     */
    fun insert(text: String, selectionStart: Int, selectionEnd: Int, reply: String): Insertion {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val before = text.substring(0, start)
        val after = text.substring(end)
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trail = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        val inserted = lead + reply + trail
        return Insertion(before + inserted + after, before.length + inserted.length)
    }
}
