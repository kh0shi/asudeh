package ir.asudehapp.sms.app

import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.persian.PersianText
import java.util.Calendar

/**
 * متن‌های فارسی رابط.
 *
 * فعلاً اینجا و نه در `strings.xml` نگه داشته می‌شوند؛ ترجمهٔ انگلیسی (D12)
 * هنوز انجام نشده و وقتی انجام شود همین‌ها به منابع منتقل می‌شوند.
 */
object Texts {
    const val APP_NAME = "آسوده"
    const val TAGLINE = "پیامک، بدون تبلیغ"
    const val INBOX = "صندوق"
    const val PROMO_FOLDER = "تبلیغات"
    const val SCAM_FOLDER = "کلاهبرداری"
    const val BECOME_DEFAULT = "آسوده را اپ پیامک اصلی کن"
    const val NOT_DEFAULT_TITLE = "آسوده هنوز اپ پیامک پیش‌فرض نیست"
    const val NOT_DEFAULT_BODY =
        "تا وقتی پیش‌فرض نشود، پیامک‌های تازه به آسوده نمی‌رسند. " +
            "پیامک‌های موجود را می‌توانید همین‌جا ببینید."
    const val EMPTY_INBOX = "پیامکی نیست"
    const val EMPTY_PROMO = "هیچ تبلیغی پنهان نشده است"
    const val EMPTY_SCAM = "پیامک کلاهبرداری‌ای پیدا نشده است"
    const val UNKNOWN_SENDER = "فرستندهٔ ناشناس"
    const val WHY_HERE = "چرا اینجاست؟"
    const val RESCUE = "این تبلیغ نیست"
    const val BLOCK = "همیشه تبلیغ"
    const val UNSUB = "لغو با «۱۱»"
    const val MARK_ALL_READ = "همه خوانده شد"
    const val EMPTY_FOLDER = "خالی کردن پوشه"
    const val EMPTY_FOLDER_CONFIRM = "خالی کردن پوشه، پیامک‌ها را برای همیشه پاک می‌کند."
    const val CANCEL = "بی‌خیال"
    const val CONFIRM_DELETE = "پاک کن"
    const val SUSPECT_WARNING = "مراقب باشید: این پیامک نشانه‌های کلاهبرداری دارد"
    const val LINKS_DISABLED = "لینک‌های این پیامک تا تأیید شما باز نمی‌شوند."
    const val RESCUE_FOLLOW_UP = "پیامک‌های بعدی این فرستنده هم همیشه در صندوق بیاید؟"
    const val YES = "بله"
    const val ONLY_THIS = "فقط همین"
    const val SHOW_ON_DOUBT =
        "پیامکی که آسوده دربارهٔ آن مطمئن نباشد، در صندوق می‌ماند. " +
            "ترجیح می‌دهیم یک تبلیغ رد شود تا یک پیامک مهم پنهان شود."
    const val SYNCING = "در حال بررسی پیامک‌های قدیمی…"

    fun folderName(folder: Folder): String = when (folder) {
        Folder.INBOX -> INBOX
        Folder.PROMO -> PROMO_FOLDER
        Folder.SCAM -> SCAM_FOLDER
    }

    /** نوار جمع‌شدهٔ `HiddenRun` داخل یک گفتگو (D43). */
    fun hiddenRun(count: Int): String =
        PersianText.persianDigits("$count پیامک تبلیغاتی")

    fun newCount(count: Int): String = PersianText.persianDigits("$count جدید")

    fun sender(address: String): String =
        address.ifBlank { UNKNOWN_SENDER }.let(PersianText::persianDigits)

    /**
     * یک جملهٔ انسانی برای «چرا اینجاست؟» (D44). ساختن متن از روی `ReasonCode`
     * انجام می‌شود، تا `:core:classifier` به منابع اندروید وابسته نشود.
     */
    fun reason(message: MessageEntity): String {
        val args = message.reasonArgs.split('|').filter { it.isNotBlank() }
        fun arg(index: Int): String = args.getOrElse(index) { "…" }
        return when (message.reasonCode) {
            ReasonCode.AD_LINE_AND_PROMO_WORDS ->
                "از خط تبلیغاتی ${sender(message.address)} و شامل «${arg(0)}» است."

            ReasonCode.PROMO_WORDS -> "شامل «${arg(0)}» است."
            ReasonCode.BLOCKED_BY_USER ->
                "شما گفته‌اید تبلیغ‌های ${sender(message.address)} همیشه پنهان شود."

            ReasonCode.ALLOWED_BY_USER ->
                "شما ${sender(message.address)} را به فهرست سفید اضافه کرده‌اید."

            ReasonCode.OTP_PATTERN -> "رمز یکبار مصرف است، پس همیشه در صندوق می‌ماند."
            ReasonCode.BANK_PATTERN -> "پیامک بانکی است، پس همیشه در صندوق می‌ماند."
            ReasonCode.SERVICE_PATTERN -> "پیامک خدماتی است و بی‌صدا اعلان می‌شود."
            ReasonCode.PERSONAL_NUMBER -> "از یک شمارهٔ موبایل آمده، پس خودکار پنهان نمی‌شود."
            ReasonCode.KNOWN_CONTACT -> "از یکی از مخاطب‌های شماست، پس خودکار پنهان نمی‌شود."
            ReasonCode.SENDER_SPOOF ->
                "خود را «${arg(0)}» معرفی کرده، ولی سرشماره‌اش رسمی نیست."

            ReasonCode.DOMAIN_SPOOF ->
                "لینک «${arg(1)}» شبیه دامنهٔ رسمی «${arg(2)}» است ولی خودش نیست."

            ReasonCode.SUSPICIOUS_LINK -> "لینک «${arg(0)}» مشکوک است."
            ReasonCode.NOT_SURE -> SHOW_ON_DOUBT
            ReasonCode.CLASSIFIER_FAILED ->
                "بررسی این پیامک کامل نشد، پس در صندوق مانده است."
        }
    }

    /** تاریخ شمسی و ارقام فارسی، پیش‌فرض رابط‌اند (D50). */
    fun timestamp(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val date = JalaliDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        val today = Calendar.getInstance()
        val sameDay = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            PersianText.persianDigits(
                "%02d:%02d".format(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                ),
            )
        } else {
            date.formatShort()
        }
    }
}
