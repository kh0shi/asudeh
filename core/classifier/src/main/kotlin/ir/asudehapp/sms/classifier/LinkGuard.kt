package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.DetectedLink

/**
 * تشخیص لینک با پیاده‌سازی خودمان، نه با `Linkify` (D37).
 *
 * `LinkGuard` همیشه و مستقل از `Allowlist` اجرا می‌شود (اصل ۵، استثنا).
 * پیش‌نمایش لینک هرگز نداریم، چون اپ اینترنت ندارد (`NoNet`).
 */
object LinkGuard {

    /**
     * گروه ۱ پیشوند (`https://` یا `www.`) و گروه ۲ میزبان است. پس از میزبان هر
     * نویسه‌ای جز حرف و رقم و `-` و `_` می‌تواند بیاید: `»`، `!`، `"`، `:` و…
     */
    private val URL_REGEX = Regex(
        """((?:https?|ftp)://|www\.)?([\p{L}\p{N}][\p{L}\p{N}\-._]*\.(?:[\p{L}]{2,24}))(?::\d{2,5})?(?![\p{L}\p{N}\-_])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * دامنه‌های سطح بالا که نباید هر «کلمه.کلمه» را لینک بشماریم. لینکی که با
     * `https://` یا `www.` شروع شود، با هر دامنهٔ سطح بالایی لینک است.
     */
    private val KNOWN_TLDS = setOf(
        "ir", "com", "net", "org", "info", "biz", "co", "io", "me", "app", "site",
        "online", "xyz", "top", "shop", "store", "club", "live", "icu", "cc", "tk",
        "ru", "cn", "uk", "de", "fr", "in", "pro", "vip", "link", "click", "help",
        "ly", "gd", "gl", "to", "st", "su", "im", "ai", "sh", "ws", "cf", "ga",
        "ml", "space", "website", "fun", "life", "world", "today", "win", "bid",
        "pw", "sbs", "cfd", "buzz", "cyou", "us", "tr", "ae", "eu", "tv", "cloud",
        "work", "rest", "bar", "lol", "monster", "quest", "email", "support",
        "services", "digital", "network", "page", "dev", "gq", "tel", "mobi", "ink",
    )

    /**
     * نویسه‌هایی که شبیه حروف لاتین دیده می‌شوند و در جعل دامنه به کار می‌روند:
     * سیریلیک، یونانی و ارقام.
     */
    private val CONFUSABLES = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x',
        'у' to 'y', 'і' to 'i', 'ѕ' to 's', 'ԁ' to 'd', 'ӏ' to 'l', 'ν' to 'v',
        'α' to 'a', 'ο' to 'o', 'ρ' to 'p', 'τ' to 't', 'κ' to 'k',
        '0' to 'o', '1' to 'l', '3' to 'e', '5' to 's',
    )

    fun extract(body: String): List<DetectedLink> {
        val found = LinkedHashMap<String, DetectedLink>()
        for (match in URL_REGEX.findAll(body)) {
            val explicit = match.groupValues[1].isNotEmpty()
            val host = match.groupValues[2].lowercase().trimEnd('.')
            val tld = host.substringAfterLast('.', "")
            if (!explicit && tld !in KNOWN_TLDS) continue
            if (host.count { it == '.' } == 0) continue
            val display = host.removePrefix("www.")
            found.putIfAbsent(
                host,
                DetectedLink(
                    raw = match.value,
                    host = host,
                    displayHost = display,
                    isPunycode = display.split('.').any { it.startsWith("xn--") },
                    hasConfusableChars = display.any { it in CONFUSABLES && !it.isDigit() },
                ),
            )
        }
        return found.values.toList()
    }

    /** دامنهٔ قابل ثبت، یعنی دو برچسب آخر (برای `co.ir` و مانند آن سه برچسب). */
    fun registrableDomain(host: String): String {
        val labels = host.removePrefix("www.").split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in SECOND_LEVEL_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
    }

    private val SECOND_LEVEL_SUFFIXES = setOf(
        "co.ir", "ac.ir", "gov.ir", "id.ir", "net.ir", "org.ir", "sch.ir",
        "co.uk", "org.uk", "com.au", "co.jp",
    )

    /**
     * آیا این میزبان دارد دامنهٔ رسمی را جعل می‌کند؟ برابری یا زیردامنهٔ رسمی
     * بودن جعل نیست.
     */
    fun isLookalike(host: String, officialDomain: String): Boolean {
        val official = officialDomain.lowercase().removePrefix("www.")
        val clean = host.lowercase().removePrefix("www.")
        if (clean == official || clean.endsWith(".$official")) return false

        val registrable = registrableDomain(clean)
        val officialLabel = official.substringBefore('.')

        // دامنهٔ رسمی جایی در نام آمده ولی دامنهٔ قابل ثبت چیز دیگری است:
        // bmi-ir.com یا bmi.ir.login-secure.com
        if (clean.contains(official.replace(".", "-")) ) return true
        if (clean.contains(official) && registrable != official) return true
        if (officialLabel.length >= 3 && registrable.substringBefore('.').let {
                it != officialLabel && it.contains(officialLabel)
            }
        ) {
            return true
        }

        // نویسه‌های شبیه‌هم و punycode
        val deconfused = registrable.map { CONFUSABLES[it] ?: it }.joinToString("")
        val officialDeconfused = official.map { CONFUSABLES[it] ?: it }.joinToString("")
        if (deconfused == officialDeconfused) return true
        if (registrable.split('.').any { it.startsWith("xn--") }) return true

        // «rn» به‌جای «m» و «vv» به‌جای «w»: brni.ir در برابر bmi.ir (D37).
        if (normalizeForComparison(registrable) == normalizeForComparison(official)) return true

        // غلط املایی نزدیک: bmi.ir در برابر bnii.ir
        val registrableLabel = registrable.substringBefore('.')
        if (officialLabel.length >= 4 && editDistance(registrableLabel, officialLabel) in 1..2) return true

        return false
    }

    /** برای «rn» به‌جای «m» که فاصلهٔ ویرایشی آن ۲ است ولی چشم آن را نمی‌بیند. */
    fun normalizeForComparison(host: String): String =
        host.map { CONFUSABLES[it] ?: it }.joinToString("").replace("rn", "m").replace("vv", "w")

    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous.indices.forEach { previous[it] = current[it] }
        }
        return previous[b.length]
    }
}
