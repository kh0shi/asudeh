package ir.asudehapp.sms.telephony

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.Notifier
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.NotificationBehavior

/**
 * کانال‌های اعلان (D38). شناسهٔ کانال بعد از انتشار عوض‌شدنی نیست، پس این
 * رشته‌ها ثابت می‌مانند.
 */
object NotificationChannels {
    const val PERSONAL = "asudeh.personal"
    const val OTP = "asudeh.otp"
    const val BANK = "asudeh.bank"
    const val SERVICE = "asudeh.service"
    const val SUSPECT = "asudeh.suspect"
    const val DIGEST = "asudeh.digest"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(PERSONAL, "شخصی", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(OTP, "رمز یکبار", NotificationManager.IMPORTANCE_HIGH).apply {
                // روی صفحهٔ قفل فقط عنوان دیده می‌شود، نه خود رمز (D39).
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(BANK, "بانکی", NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            // خدماتی به‌طور پیش‌فرض بی‌صداست (اصل ۳).
            NotificationChannel(SERVICE, "خدماتی", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(SUSPECT, "مشکوک", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(DIGEST, "خلاصه", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannels(channels)
    }
}

/**
 * اعلان یک پیامک تازه.
 *
 * تبلیغ و کلاهبرداری هیچ اعلانی ندارند (D38)، ولی پیامک همچنان در اپ هست:
 * «پنهان، نه پاک» (اصل ۱).
 */
class AsudehNotifier(
    private val context: Context,
    private val openConversation: (Long) -> Intent?,
) : Notifier {

    override suspend fun notify(message: ClassifiedMessage) {
        if (message.placement.notification == NotificationBehavior.NONE) return
        NotificationChannels.ensure(context)

        val manager = NotificationManagerCompat.from(context)
        val channel = channelFor(message)
        val silent = message.placement.notification == NotificationBehavior.SILENT

        val title = when {
            message.placement.showWarning -> "مراقب باشید: پیامک مشکوک"
            else -> message.raw.address.ifBlank { "فرستندهٔ ناشناس" }
        }
        val text = when {
            message.verdict.category == Category.OTP -> "رمز یکبار مصرف دریافت شد"
            message.placement.showWarning -> "این پیامک نشانه‌های کلاهبرداری دارد"
            else -> message.raw.body.take(MAX_PREVIEW)
        }

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(message.raw.receivedAt)
            .setAutoCancel(true)
            .setSilent(silent)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)

        if (message.verdict.category == Category.OTP || message.verdict.category == Category.BANK) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }

        openConversation(message.threadId)?.let { intent ->
            builder.setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    message.threadId.toInt(),
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        runCatching { manager.notify(message.providerId.toInt(), builder.build()) }
    }

    private fun channelFor(message: ClassifiedMessage): String = when {
        message.placement.showWarning -> NotificationChannels.SUSPECT
        message.verdict.category == Category.OTP -> NotificationChannels.OTP
        message.verdict.category == Category.BANK -> NotificationChannels.BANK
        message.verdict.category == Category.SERVICE -> NotificationChannels.SERVICE
        else -> NotificationChannels.PERSONAL
    }

    private companion object {
        const val MAX_PREVIEW = 200
    }
}
