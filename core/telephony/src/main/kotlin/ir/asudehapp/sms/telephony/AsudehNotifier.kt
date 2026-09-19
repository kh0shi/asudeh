package ir.asudehapp.sms.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.Notifier
import ir.asudehapp.sms.data.MessageEntity
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
    private val openConversation: (Long) -> Intent,
) : Notifier {

    @SuppressLint("MissingPermission")
    override suspend fun notify(message: ClassifiedMessage) {
        if (message.placement.notification == NotificationBehavior.NONE) return
        if (!canNotify()) return
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

        builder.setContentIntent(contentIntent(message.threadId))
        runCatching { manager.notify(notificationId(message.providerId), builder.build()) }
    }

    /** ارسال ناموفق هرگز بی‌صدا نیست. */
    @SuppressLint("MissingPermission")
    fun notifySendFailed(message: MessageEntity) {
        if (!canNotify()) return
        NotificationChannels.ensure(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.PERSONAL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("پیامک ارسال نشد")
            .setContentText("به ${message.address}. برای ارسال دوباره، گفتگو را باز کنید.")
            .setContentIntent(contentIntent(message.threadId))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(message.providerId) xor SEND_FAILED_SALT, notification)
        }
    }

    /**
     * MMS هنوز نمایش داده نمی‌شود (D26)، ولی کاربر باید بداند که رسیده است.
     * [saved] یعنی اعلان MMS در provider نوشته شد.
     */
    @SuppressLint("MissingPermission")
    fun notifyMms(from: String?, saved: Boolean) {
        if (!canNotify()) return
        NotificationChannels.ensure(context)
        val sender = from?.takeIf { it.isNotBlank() } ?: "فرستندهٔ ناشناس"
        val text = if (saved) {
            "آسوده هنوز پیام چندرسانه‌ای را نمایش نمی‌دهد. پیام در حافظهٔ پیامک گوشی ثبت شد."
        } else {
            "آسوده هنوز پیام چندرسانه‌ای را نمایش نمی‌دهد و نتوانست آن را ثبت کند."
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.PERSONAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("پیام چندرسانه‌ای (MMS) از $sender")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
        }
    }

    private fun contentIntent(threadId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(threadId),
        openConversation(threadId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** شناسهٔ اعلان از شناسهٔ ۶۴بیتی، بدون اینکه دو پیامک روی هم بیفتند. */
    private fun notificationId(id: Long): Int = (id xor (id ushr 32)).toInt()

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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
        const val SEND_FAILED_SALT = 0x5E1D
    }
}
