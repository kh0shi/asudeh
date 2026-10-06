package ir.asudehapp.sms.model

import ir.asudehapp.sms.persian.PersianText

/**
 * قاعدهٔ الگو برای فرستنده (ADR-0016): یک پیشوند سرشماره با یک `*` در انتها،
 * مثل `5000*` یا `+98990*`. عمداً ساده است: فقط همان یک `*` آخر، نه `?` و نه
 * `*` وسط الگو.
 *
 * پیشوند مثل سرشمارهٔ کامل یکسان می‌شود ([Addresses.normalize])، با ارقام
 * فارسی و عربی لاتین‌شده، تا `+98990*` و `0990*` و `۰۹۹۰*` یک قاعده باشند.
 */
object SenderPattern {

    const val WILDCARD: Char = '*'

    /** کوتاه‌تر از این، الگو تقریباً همهٔ پیامک‌ها را می‌گیرد. */
    const val MIN_PREFIX: Int = 3

    private val SEPARATORS = setOf(' ', '-', '(', ')')

    /** یک قاعدهٔ الگو، آمادهٔ تطبیق. [pattern] همان چیزی است که کاربر نوشته. */
    data class Rule(val prefix: String, val pattern: String, val allow: Boolean)

    /**
     * شکل یکسان‌شدهٔ پیشوند، که کلید اصلی جدول هم هست؛ یا `null` اگر الگو
     * پذیرفتنی نیست (بدون `*` آخر، `*` یا نویسهٔ دیگری وسطش، یا پیشوند کوتاه).
     */
    fun normalize(pattern: String): String? {
        val text = PersianText.latinDigits(pattern.trim())
        if (text.lastOrNull() != WILDCARD) return null
        val compact = text.dropLast(1).filterNot { it in SEPARATORS }
        // «+98» و «0098» همان «0» است، حتی وقتی پیشوند کوتاه‌تر از یک شمارهٔ
        // کامل است؛ وگرنه `+98990*` هرگز با `09901234567` جور نمی‌شد.
        val local = when {
            compact.startsWith("+98") -> "0" + compact.removePrefix("+98")
            compact.startsWith("0098") -> "0" + compact.removePrefix("0098")
            else -> compact
        }
        // `Addresses.normalize` هر نویسهٔ دیگری (`*` دوم، `?`، `+` وسط) را نگه
        // می‌دارد، پس همین یک بررسی همه را رد می‌کند.
        val prefix = Addresses.normalize(local)
        return prefix.takeIf { it.length >= MIN_PREFIX && it.all(Char::isLetterOrDigit) }
    }

    /** سرشمارهٔ ورودی، به همان شکلی که پیشوندها با آن مقایسه می‌شوند. */
    fun addressKey(address: String): String = Addresses.normalize(PersianText.latinDigits(address))

    /**
     * قاعده‌ای که روی این سرشماره اجرا می‌شود: بلندترین پیشوند برنده است، و
     * اگر دو قاعده هم‌طول باشند، فهرست سفید (در تردید، نشان بده).
     */
    fun bestMatch(rules: List<Rule>, address: String): Rule? {
        if (rules.isEmpty()) return null
        val key = addressKey(address)
        var best: Rule? = null
        for (rule in rules) {
            if (!key.startsWith(rule.prefix)) continue
            val current = best
            val better = current == null ||
                rule.prefix.length > current.prefix.length ||
                (rule.prefix.length == current.prefix.length && rule.allow && !current.allow)
            if (better) best = rule
        }
        return best
    }
}
