package ir.asudehapp.sms.app

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.persian.PersianText
import ir.asudehapp.sms.persian.ScheduleChoice
import ir.asudehapp.sms.persian.ScheduleDay
import ir.asudehapp.sms.persian.SchedulePreset
import ir.asudehapp.sms.persian.SendSchedule
import java.time.ZoneId
import java.util.Calendar

/** ارقام فارسی در رابط (D50)؛ از تنظیمات می‌آید. */
val LocalPersianDigits = compositionLocalOf { true }

/** نام مخاطب هر سرشماره، اگر اجازهٔ مخاطب‌ها داده شده باشد. */
val LocalContactNames = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * کمک‌کننده‌های متن رابط. خود متن‌ها در `strings.xml` هستند (D12)؛ اینجا فقط
 * ارقام فارسی (D50) و ساختن جمله از روی `ReasonCode` انجام می‌شود.
 */
object Texts {

    /**
     * آرگومان‌ها دست نمی‌خورند: نام دامنه یا کلیدواژه‌ای که در «چرا اینجاست؟»
     * نشان داده می‌شود باید همان باشد که در پیامک آمده است.
     */
    @Composable
    fun text(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args)

    @Composable
    fun count(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        digits(pluralStringResource(id, count, count, *args))

    /** ارقام رابط؛ فارسی یا لاتین، بسته به تنظیمات. متن پیامک از این راه نمی‌گذرد. */
    @Composable
    @ReadOnlyComposable
    fun digits(text: String): String =
        if (LocalPersianDigits.current) PersianText.persianDigits(text) else PersianText.latinDigits(text)

    @Composable
    fun folderName(folder: Folder): String = stringResource(
        when (folder) {
            Folder.INBOX -> R.string.folder_inbox
            Folder.PROMO -> R.string.folder_promo
            Folder.SCAM -> R.string.folder_scam
        },
    )

    /**
     * سرشماره داخل یک «جداسازی با اولین حرف قوی» (FSI…PDI) می‌آید: در رابط
     * راست‌به‌چپ، `+98912…` باید با همان ترتیب چپ‌به‌راست دیده شود و «+» به
     * آخرش نپرد، ولی نام فارسی فرستنده راست‌به‌چپ بماند (D50).
     */
    @Composable
    fun sender(address: String): String {
        val name = LocalContactNames.current[address]
        if (name != null) return FSI + name + PDI
        return FSI + digits(address.ifBlank { stringResource(R.string.unknown_sender) }) + PDI
    }

    /** نام یک گفتگو: برای گروه، نام همهٔ اعضا. */
    @Composable
    fun participants(addresses: List<String>): String {
        if (addresses.size <= 1) return sender(addresses.firstOrNull().orEmpty())
        return addresses.map { sender(it) }.joinToString(stringResource(R.string.list_separator))
    }

    /** نام گفتگو از روی خلاصه‌اش. */
    @Composable
    fun thread(address: String, recipients: String): String =
        if (recipients.isEmpty()) {
            sender(address)
        } else {
            participants(recipients.split(MessageEntity.RECIPIENT_SEPARATOR))
        }

    private const val FSI = "\u2068"
    private const val PDI = "\u2069"

    /**
     * یک جملهٔ انسانی برای «چرا اینجاست؟» (D44). ساختن متن از روی `ReasonCode`
     * انجام می‌شود، تا `:core:classifier` به منابع اندروید وابسته نشود.
     */
    @Composable
    fun reason(message: MessageEntity): String {
        val args = message.reasonArgs.split('|').filter { it.isNotBlank() }
        fun arg(index: Int): String = args.getOrElse(index) { "…" }
        val sender = sender(message.address)
        return when (message.reasonCode) {
            ReasonCode.AD_LINE_AND_PROMO_WORDS -> text(R.string.reason_ad_line, sender, arg(0))
            ReasonCode.PROMO_WORDS -> text(R.string.reason_promo_words, arg(0))
            ReasonCode.BLOCKED_BY_USER -> text(R.string.reason_blocked, sender)
            ReasonCode.ALLOWED_BY_USER -> text(R.string.reason_allowed, sender)
            ReasonCode.ALLOWED_KEYWORD -> text(R.string.reason_allowed_keyword, arg(0))
            ReasonCode.BLOCKED_KEYWORD -> text(R.string.reason_blocked_keyword, arg(0))
            ReasonCode.OTP_PATTERN -> text(R.string.reason_otp)
            ReasonCode.BANK_PATTERN -> text(R.string.reason_bank)
            ReasonCode.SERVICE_PATTERN -> text(R.string.reason_service)
            ReasonCode.PERSONAL_NUMBER -> text(R.string.reason_personal)
            ReasonCode.KNOWN_CONTACT -> text(R.string.reason_contact)
            ReasonCode.SENDER_SPOOF -> text(R.string.reason_sender_spoof, arg(0))
            ReasonCode.DOMAIN_SPOOF -> text(R.string.reason_domain_spoof, arg(1), arg(2))
            ReasonCode.SUSPICIOUS_LINK -> text(R.string.reason_suspicious_link, arg(0))
            ReasonCode.NOT_SURE -> text(R.string.show_on_doubt)
            ReasonCode.CLASSIFIER_FAILED -> text(R.string.reason_classifier_failed)
        }
    }

    /** «فردا ساعت ۰۸:۰۰ فرستاده می‌شود» برای یک پیامک زمان‌بندی‌شده (ADR-0011). */
    @Composable
    fun scheduledFor(millis: Long): String {
        val at = SendSchedule.describe(millis, System.currentTimeMillis(), ZoneId.systemDefault())
        val clock = digits(at.clock())
        return when (at.day) {
            ScheduleDay.TODAY -> text(R.string.scheduled_today, clock)
            ScheduleDay.TOMORROW -> text(R.string.scheduled_tomorrow, clock)
            ScheduleDay.LATER -> text(R.string.scheduled_later, digits(at.date.formatShort()), clock)
        }
    }

    /** نام یکی از زمان‌های پیشنهادی در برگهٔ «بعداً بفرست». */
    @Composable
    fun scheduleChoice(choice: ScheduleChoice): String {
        val at = SendSchedule.describe(choice.atMillis, System.currentTimeMillis(), ZoneId.systemDefault())
        val clock = digits(at.clock())
        return when (choice.preset) {
            SchedulePreset.TONIGHT -> text(R.string.schedule_tonight, clock)
            SchedulePreset.TOMORROW_MORNING -> text(R.string.schedule_tomorrow_morning, clock)
            SchedulePreset.TOMORROW_AFTERNOON -> text(R.string.schedule_tomorrow_afternoon, clock)
        }
    }

    /**
     * تاریخ شمسی و ارقام فارسی، پیش‌فرض رابط‌اند (D50). پیامک امروز فقط ساعتش
     * را نشان می‌دهد؛ برای پیامک قدیمی‌تر، با [withClock] ساعت هم کنار تاریخ
     * می‌آید (پیش‌فرض تنظیمات).
     */
    @Composable
    fun timestamp(millis: Long, withClock: Boolean = false): String =
        digits(timestampText(millis, withClock))

    private fun timestampText(millis: Long, withClock: Boolean): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val date = JalaliDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        val today = Calendar.getInstance()
        val sameDay = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
        val clock = "%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
        return when {
            sameDay -> clock
            withClock -> "${date.formatShort()} $clock"
            else -> date.formatShort()
        }
    }
}
