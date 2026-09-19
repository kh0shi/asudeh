package ir.asudehapp.sms.app

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.persian.PersianText
import java.util.Calendar

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
        PersianText.persianDigits(pluralStringResource(id, count, count, *args))

    @Composable
    fun folderName(folder: Folder): String = stringResource(
        when (folder) {
            Folder.INBOX -> R.string.folder_inbox
            Folder.PROMO -> R.string.folder_promo
            Folder.SCAM -> R.string.folder_scam
        },
    )

    @Composable
    fun sender(address: String): String =
        PersianText.persianDigits(address.ifBlank { stringResource(R.string.unknown_sender) })

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
