package ir.asudehapp.sms.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** زمان‌بندی `Digest` (D40). */
enum class DigestFrequency { DAILY, WEEKLY, OFF }

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
        put(KEY_DYNAMIC_COLOR, dynamicColor.toString())
        put(KEY_PERSIAN_DIGITS, persianDigits.toString())
        put(KEY_SHOW_REASON, showReasonEverywhere.toString())
    }

    /** بازگردانی از پشتیبان. کلید ناشناخته یا مقدار خراب نادیده گرفته می‌شود. */
    fun import(values: Map<String, String>) {
        values[KEY_DELIVERY_REPORTS]?.toBooleanStrictOrNull()?.let { deliveryReports = it }
        values[KEY_DIGEST]?.let { digest = enumOr(it, digest) }
        values[KEY_THEME]?.let { theme = enumOr(it, theme) }
        values[KEY_DYNAMIC_COLOR]?.toBooleanStrictOrNull()?.let { dynamicColor = it }
        values[KEY_PERSIAN_DIGITS]?.toBooleanStrictOrNull()?.let { persianDigits = it }
        values[KEY_SHOW_REASON]?.toBooleanStrictOrNull()?.let { showReasonEverywhere = it }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val NAME = "asudeh-settings"
        const val KEY_DELIVERY_REPORTS = "delivery_reports"
        const val KEY_DIGEST = "digest"
        const val KEY_LAST_DIGEST = "last_digest_at"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PERSIAN_DIGITS = "persian_digits"
        const val KEY_SHOW_REASON = "show_reason_everywhere"
    }
}
