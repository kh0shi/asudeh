package ir.asudehapp.sms.persian

/**
 * نرمال‌سازی متن فارسی برای جستجو و طبقه‌بند (D50).
 *
 * **متن پیامک هرگز تغییر نمی‌کند.** خروجی این توابع فقط برای مقایسه و جستجو
 * به کار می‌رود و آنچه به کاربر نشان داده می‌شود همان متن اصلی است.
 */
object PersianText {

    private const val ZWNJ = '‌'

    /** اعراب، کشیده و نویسه‌های کنترلی جهت که در مقایسه بی‌اثرند. */
    private val REMOVED = buildSet {
        addAll('ً'..'ٟ')  // اعراب
        add('ـ')               // کشیده (ـ)
        add('ٰ')               // الف خنجری
        addAll('ۖ'..'ۭ')  // نشانه‌های قرآنی
        addAll('‎'..'‏')  // LRM و RLM
        addAll('‪'..'‮')  // نویسه‌های جهت‌دهی
        addAll('\u2066'..'\u2069')  // جداکننده‌های جهت (isolate)
        add('\u200B')  // فاصلهٔ صفر
        add('\u200D')  // اتصال‌دهندهٔ صفر (ZWJ)
        add('\u2060')  // word joiner
        add('\u00AD')  // خط تیرهٔ نرم
        add('\u180E')
        add('﻿')
    }

    private val LETTER_MAP = mapOf(
        'ي' to 'ی', 'ى' to 'ی', 'ﻯ' to 'ی', 'ﻱ' to 'ی',
        'ك' to 'ک', 'ﻙ' to 'ک', 'ﻚ' to 'ک',
        'ۀ' to 'ه', 'ة' to 'ه',
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا',
        'ؤ' to 'و',
        'ئ' to 'ی',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٪' to '%', '،' to ',', '؛' to ';', '؟' to '?',
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /**
     * یکی کردن ی/ي و ک/ك، تبدیل ارقام فارسی و عربی به لاتین، حذف اعراب و
     * نیم‌فاصله، و جمع کردن فاصله‌های پیاپی. خروجی با حروف کوچک لاتین است.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (raw in text) {
            if (raw in REMOVED) continue
            val ch = LETTER_MAP[raw] ?: raw
            when {
                ch == ZWNJ -> builder.append(' ')
                ch.isWhitespace() -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
            .replace(Regex(" {2,}"), " ")
            .trim()
            .lowercase()
    }

    /**
     * آیا [word] به‌صورت یک کلمه یا عبارت کامل در [normalizedText] آمده است؟
     * پیش و پس از آن نباید حرف یا رقم باشد، تا «چک» درون «کوچک» یا «bmi» درون
     * «submit» پیدا نشود. هر دو ورودی باید نرمال‌شده باشند.
     */
    fun containsWord(normalizedText: String, word: String): Boolean {
        if (word.isEmpty()) return false
        var from = 0
        while (true) {
            val at = normalizedText.indexOf(word, from)
            if (at < 0) return false
            val end = at + word.length
            val startOk = at == 0 || !normalizedText[at - 1].isLetterOrDigit()
            val endOk = end == normalizedText.length || !normalizedText[end].isLetterOrDigit()
            if (startOk && endOk) return true
            from = at + 1
        }
    }

    /** فقط ارقام را لاتین می‌کند و بقیهٔ متن را دست نمی‌زند. */
    fun latinDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            )
        }
    }

    /** ارقام فارسی فقط در رابط استفاده می‌شوند (D50). */
    fun persianDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
        }
    }

    /**
     * جهت یک حباب پیام بر اساس اولین حرف قوی تعیین می‌شود، نه بر اساس زبان
     * رابط (D50). برای متنی که حرف قوی ندارد، `false` برمی‌گردد.
     */
    fun isRightToLeft(text: String): Boolean {
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                -> return true

                else -> Unit
            }
        }
        return false
    }
}
