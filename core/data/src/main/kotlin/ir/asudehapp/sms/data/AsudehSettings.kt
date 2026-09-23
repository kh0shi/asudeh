package ir.asudehapp.sms.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** زمان‌بندی `Digest` (D40). */
enum class DigestFrequency { DAILY, WEEKLY, OFF }

/** زبان رابط؛ [SYSTEM] یعنی همان زبان گوشی. */
enum class AppLanguage { SYSTEM, FA, EN }

/** تقویم تاریخ‌ها؛ [AUTO] یعنی شمسی در رابط فارسی و میلادی در انگلیسی. */
enum class DateStyle { AUTO, JALALI, GREGORIAN }

/** تم رابط (D49، D52). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * تنظیمات کاربر (D52). در انتقال گوشی‌به‌گوشی و در پشتیبان فایل (D57) منتقل
 * می‌شوند. هر چیزی که اینجاست از دست رفتنش فقط یعنی برگشتن به پیش‌فرض.
 */
class AsudehSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** گزارش تحویل (D53)؛ به‌طور پیش‌فرض خاموش. */
    var deliveryReports: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_REPORTS, false)
        set(value) = prefs.edit().putBoolean(KEY_DELIVERY_REPORTS, value).apply()

    var digest: DigestFrequency
        get() = enumOr(prefs.getString(KEY_DIGEST, null), DigestFrequency.DAILY)
        set(value) = prefs.edit().putString(KEY_DIGEST, value.name).apply()

    /** زمان آخرین `Digest`؛ شمارش پیامک‌های پنهان از این لحظه است. */
    var lastDigestAt: Long
        get() = prefs.getLong(KEY_LAST_DIGEST, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DIGEST, value).apply()

    /** زبان رابط. پیش‌فرض زبان گوشی است. */
    var language: AppLanguage
        get() = enumOr(prefs.getString(KEY_LANGUAGE, null), AppLanguage.SYSTEM)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    var dateStyle: DateStyle
        get() = enumOr(prefs.getString(KEY_DATE_STYLE, null), DateStyle.AUTO)
        set(value) = prefs.edit().putString(KEY_DATE_STYLE, value.name).apply()

    var theme: ThemeMode
        get() = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** رنگ پویای اندروید (Material You)؛ پیش‌فرض رنگ برند است (D49). */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    /** ارقام فارسی در رابط (D50). متن پیامک هرگز تغییر نمی‌کند. */
    var persianDigits: Boolean
        get() = prefs.getBoolean(KEY_PERSIAN_DIGITS, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSIAN_DIGITS, value).apply()

    /** «پیشرفته»: نمایش دلیل روی همهٔ پیامک‌ها، نه فقط پیامک‌های پنهان (D52). */
    var showReasonEverywhere: Boolean
        get() = prefs.getBoolean(KEY_SHOW_REASON, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_REASON, value).apply()

    /** ساعت هر پیامک، زیر خودش. پیش‌فرض روشن است. */
    var showMessageClock: Boolean
        get() = prefs.getBoolean(KEY_MESSAGE_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGE_CLOCK, value).apply()

    /**
     * دریافت خودکار پیام چندرسانه‌ای از MMSC. خاموش که باشد، اعلانِ پیام در
     * گفتگو با دکمهٔ «دریافت» می‌ماند؛ هیچ پیامی گم نمی‌شود.
     */
    var mmsAutoDownload: Boolean
        get() = prefs.getBoolean(KEY_MMS_AUTO_DOWNLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_MMS_AUTO_DOWNLOAD, value).apply()

    /** فرستادن پیوست و پیام گروهی (MMS). خاموش که باشد، فقط پیامک ساده فرستاده می‌شود. */
    var mmsSending: Boolean
        get() = prefs.getBoolean(KEY_MMS_SENDING, true)
        set(value) = prefs.edit().putBoolean(KEY_MMS_SENDING, value).apply()

    /** قاعده‌های پیش‌فرضی که کاربر خاموش کرده است؛ نام‌های `DefaultRule` (ADR-0012). */
    var disabledDefaultRules: Set<String>
        get() = readSet(KEY_DISABLED_DEFAULTS)
        set(value) = writeSet(KEY_DISABLED_DEFAULTS, value)

    /** کلیدواژه‌های پیش‌فرضی که کاربر خاموش کرده است؛ شناسه‌های `DefaultRules` (ADR-0012). */
    var disabledDefaultKeywords: Set<String>
        get() = readSet(KEY_DISABLED_KEYWORDS)
        set(value) = writeSet(KEY_DISABLED_KEYWORDS, value)

    private fun readSet(key: String): Set<String> =
        prefs.getString(key, null)?.split(SET_SEPARATOR)?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private fun writeSet(key: String, value: Set<String>) {
        prefs.edit().putString(key, value.joinToString(SET_SEPARATOR)).apply()
    }

    /** هر تغییر تنظیمات؛ رابط با آن خودش را به‌روز می‌کند. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** برای پشتیبان فایل (D57): فقط کلیدهای شناخته‌شده. */
    fun export(): Map<String, String> = buildMap {
        put(KEY_DELIVERY_REPORTS, deliveryReports.toString())
        put(KEY_DIGEST, digest.name)
        put(KEY_THEME, theme.name)
        put(KEY_LANGUAGE, language.name)
        put(KEY_DATE_STYLE, dateStyle.name)
        put(KEY_DYNAMIC_COLOR, dynamicColor.toString())
        put(KEY_PERSIAN_DIGITS, persianDigits.toString())
        put(KEY_SHOW_REASON, showReasonEverywhere.toString())
        put(KEY_MESSAGE_CLOCK, showMessageClock.toString())
        put(KEY_MMS_AUTO_DOWNLOAD, mmsAutoDownload.toString())
        put(KEY_MMS_SENDING, mmsSending.toString())
        put(KEY_DISABLED_DEFAULTS, disabledDefaultRules.joinToString(SET_SEPARATOR))
        put(KEY_DISABLED_KEYWORDS, disabledDefaultKeywords.joinToString(SET_SEPARATOR))
    }

    /** بازگردانی از پشتیبان. کلید ناشناخته یا مقدار خراب نادیده گرفته می‌شود. */
    fun import(values: Map<String, String>) {
        values[KEY_DELIVERY_REPORTS]?.toBooleanStrictOrNull()?.let { deliveryReports = it }
        values[KEY_DIGEST]?.let { digest = enumOr(it, digest) }
        values[KEY_THEME]?.let { theme = enumOr(it, theme) }
        values[KEY_LANGUAGE]?.let { language = enumOr(it, language) }
        values[KEY_DATE_STYLE]?.let { dateStyle = enumOr(it, dateStyle) }
        values[KEY_DYNAMIC_COLOR]?.toBooleanStrictOrNull()?.let { dynamicColor = it }
        values[KEY_PERSIAN_DIGITS]?.toBooleanStrictOrNull()?.let { persianDigits = it }
        values[KEY_SHOW_REASON]?.toBooleanStrictOrNull()?.let { showReasonEverywhere = it }
        values[KEY_MESSAGE_CLOCK]?.toBooleanStrictOrNull()?.let { showMessageClock = it }
        values[KEY_MMS_AUTO_DOWNLOAD]?.toBooleanStrictOrNull()?.let { mmsAutoDownload = it }
        values[KEY_MMS_SENDING]?.toBooleanStrictOrNull()?.let { mmsSending = it }
        values[KEY_DISABLED_DEFAULTS]?.let {
            disabledDefaultRules = it.split(SET_SEPARATOR).filter(String::isNotBlank).toSet()
        }
        values[KEY_DISABLED_KEYWORDS]?.let {
            disabledDefaultKeywords = it.split(SET_SEPARATOR).filter(String::isNotBlank).toSet()
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    companion object {
        /**
         * زبان ذخیره‌شده، بدون ساختن [AsudehSettings]؛ برای `attachBaseContext` که
         * پیش از ساخته شدن هر چیز دیگری اجرا می‌شود.
         */
        fun storedLanguage(context: Context): AppLanguage {
            val name = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
            return name?.let { runCatching { enumValueOf<AppLanguage>(it) }.getOrNull() } ?: AppLanguage.SYSTEM
        }

        private const val NAME = "asudeh-settings"
        private const val KEY_DELIVERY_REPORTS = "delivery_reports"
        private const val KEY_DIGEST = "digest"
        private const val KEY_LAST_DIGEST = "last_digest_at"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DATE_STYLE = "date_style"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_PERSIAN_DIGITS = "persian_digits"
        private const val KEY_SHOW_REASON = "show_reason_everywhere"
        private const val KEY_MESSAGE_CLOCK = "show_message_clock"
        private const val KEY_MMS_AUTO_DOWNLOAD = "mms_auto_download"
        private const val KEY_MMS_SENDING = "mms_sending"
        private const val KEY_DISABLED_DEFAULTS = "disabled_default_rules"
        private const val KEY_DISABLED_KEYWORDS = "disabled_default_keywords"

        /** جداکنندهٔ عضوهای یک مجموعه در یک کلید متنی. در متن قواعد نمی‌آید. */
        private const val SET_SEPARATOR = "\n"
    }
}
