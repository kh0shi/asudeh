package ir.asudehapp.sms.persian

/**
 * طول یک پیامک در تکه‌ها (ROADMAP D2)، برای شمارندهٔ زیر جعبهٔ نوشتن.
 *
 * متنی که همه‌اش در الفبای GSM-7 باشد، ۱۶۰ نویسه در یک تکه و ۱۵۳ در هر تکهٔ
 * پیامک چندتکه جا می‌گیرد؛ نویسه‌های جدول گسترش (مثل `€` و `{`) دو جا می‌گیرند.
 * هر نویسهٔ دیگر، از جمله فارسی، پیامک را UCS-2 می‌کند: ۷۰ واحد UTF-16 در یک
 * تکه و ۶۷ در هر تکهٔ چندتکه. این همان حساب `SmsManager.divideMessage` بدون
 * جدول‌های زبان ملی است.
 */
data class SmsLength(
    /** واحدهای مصرف‌شده: نویسهٔ GSM-7 (گسترشی دوتا) یا واحد UTF-16. */
    val used: Int,
    /** گنجایش همهٔ تکه‌های فعلی روی هم؛ برای «۱۲۰/۱۳۴». */
    val capacity: Int,
    val segments: Int,
    val unicode: Boolean,
) {
    companion object {
        const val GSM_SINGLE: Int = 160
        const val GSM_MULTI: Int = 153
        const val UCS2_SINGLE: Int = 70
        const val UCS2_MULTI: Int = 67

        fun of(text: String): SmsLength {
            val gsmUnits = gsmUnits(text)
            val unicode = gsmUnits == null
            val used = gsmUnits ?: text.length
            val single = if (unicode) UCS2_SINGLE else GSM_SINGLE
            val multi = if (unicode) UCS2_MULTI else GSM_MULTI
            return if (used <= single) {
                SmsLength(used, single, 1, unicode)
            } else {
                val segments = (used + multi - 1) / multi
                SmsLength(used, segments * multi, segments, unicode)
            }
        }

        /** شمار واحدهای GSM-7، یا `null` اگر نویسه‌ای بیرون از آن باشد. */
        private fun gsmUnits(text: String): Int? {
            var units = 0
            for (ch in text) {
                units += when (ch) {
                    in GSM_BASIC -> 1
                    in GSM_EXTENSION -> 2
                    else -> return null
                }
            }
            return units
        }

        /** الفبای پایهٔ GSM 03.38. */
        private const val GSM_BASIC: String =
            "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
                "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

        /** جدول گسترش GSM 03.38؛ هر کدام با یک Escape دو جا می‌گیرد. */
        private const val GSM_EXTENSION: String = "\u000C^{}\\[~]|€"
    }
}
