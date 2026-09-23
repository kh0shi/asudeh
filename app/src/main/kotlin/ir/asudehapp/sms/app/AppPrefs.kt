package ir.asudehapp.sms.app

import android.content.Context

/**
 * چند پرچم کوچک رابط. قواعد کاربر در `rules.db` و تنظیمات در `AsudehSettings`
 * هستند، نه اینجا؛ از دست رفتن این پرچم‌ها فقط یعنی یک پرسش دوباره پرسیده شود.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** مرحلهٔ اول و دوم اولین اجرا (D47) تمام شده است. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /**
     * کاربر به برگهٔ نتیجهٔ `HistorySweep` پاسخ داده است (D47 مرحلهٔ ۴). با
     * «فعلاً نه» هم true می‌شود: پیشنهاد در `PromoFolder` می‌ماند ولی این برگه
     * دوباره باز نمی‌شود (اصل ۸).
     */
    var sweepOfferAnswered: Boolean
        get() = prefs.getBoolean(KEY_SWEEP_ANSWERED, false)
        set(value) = prefs.edit().putBoolean(KEY_SWEEP_ANSWERED, value).apply()

    /**
     * راهنمای «با کشیدن دستگیره‌ها…» یک بار دیده شده است. از آن به بعد فقط
     * دکمهٔ «تمام» زیر پیامک می‌ماند، چون کار یک بار که یاد گرفته شد، یاد
     * گرفته شده است.
     */
    var textPickHintSeen: Boolean
        get() = prefs.getBoolean(KEY_TEXT_PICK_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_TEXT_PICK_HINT, value).apply()

    private companion object {
        const val NAME = "asudeh-ui"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SWEEP_ANSWERED = "sweep_offer_answered"
        const val KEY_TEXT_PICK_HINT = "text_pick_hint_seen"
    }
}
