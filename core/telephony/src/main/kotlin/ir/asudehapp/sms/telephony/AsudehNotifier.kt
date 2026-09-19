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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ir.asudehapp.sms.classifier.OtpCode
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.Notifier
import ir.asudehapp.sms.data.DigestFrequency
import ir.asudehapp.sms.data.HiddenCount
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.ScheduledMessageEntity
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.NotificationBehavior
import ir.asudehapp.sms.model.SenderKind
import ir.asudehapp.sms.persian.PersianText

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
        fun name(id: Int) = context.getString(id)
        val channels = listOf(
            NotificationChannel(PERSONAL, name(R.string.channel_personal), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(OTP, name(R.string.channel_otp), NotificationManager.IMPORTANCE_HIGH).apply {
                // روی صفحهٔ قفل فقط عنوان دیده می‌شود، نه خود رمز (D39).
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(BANK, name(R.string.channel_bank), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            // خدماتی به‌طور پیش‌فرض بی‌صداست (اصل ۳).
            NotificationChannel(SERVICE, name(R.string.channel_service), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(SUSPECT, name(R.string.channel_suspect), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(DIGEST, name(R.string.channel_digest), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannels(channels)
    }
}

/**
 * اعلان یک پیامک تازه.
 *
 * تبلیغ و کلاهبرداری هیچ اعلانی ندارند (D38)، ولی پیامک همچنان در اپ هست:
 * «پنهان، نه پاک» (اصل ۱). هر گفتگو یک اعلان دارد که با پیامک تازه به‌روز
 * می‌شود؛ پاسخ و «خوانده شد» همان را برمی‌دارند (D41).
 */
class AsudehNotifier(
    private val context: Context,
    private val openConversation: (Long) -> Intent,
    private val openFolder: (Folder) -> Intent,
) : Notifier {

    @SuppressLint("MissingPermission")
    override suspend fun notify(message: ClassifiedMessage) {
        if (message.placement.notification == NotificationBehavior.NONE) return
        if (!canNotify()) return
        NotificationChannels.ensure(context)

        val channel = channelFor(message)
        val silent = message.placement.notification == NotificationBehavior.SILENT
        val category = message.verdict.category
        val raw = message.raw

        val title = when {
            message.placement.showWarning -> context.getString(R.string.notify_suspect_title)
            raw.isMms -> context.getString(R.string.notify_mms_title, sender(raw.address))
            else -> sender(raw.address)
        }
        val text = when {
            category == Category.OTP -> context.getString(R.string.notify_otp_text)
            message.placement.showWarning -> context.getString(R.string.notify_suspect_text)
            raw.isMms && raw.body.isBlank() -> context.getString(
                if (raw.hasImage) R.string.notify_mms_image else R.string.notify_mms_attachment,
            )
            else -> raw.body.take(MAX_PREVIEW)
        }

        val id = threadNotificationId(message.threadId)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_asudeh)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(raw.receivedAt)
            .setAutoCancel(true)
            .setSilent(silent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(message.threadId))

        if (category == Category.OTP || category == Category.BANK) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_asudeh)
                    .setContentTitle(sender(raw.address))
                    .setContentText(context.getString(R.string.notify_otp_text).takeIf { category == Category.OTP })
                    .build(),
            )
        }

        // «کپی رمز» فقط وقتی که معلوم باشد کدام عدد رمز است (D38).
        if (category == Category.OTP && !message.placement.showWarning) {
            OtpCode.extract(raw.body)?.let { code ->
                builder.addAction(
                    0,
                    context.getString(R.string.action_copy_code, code),
                    actionIntent(NotificationActionReceiver.ACTION_COPY_CODE, id, message) {
                        putExtra(NotificationActionReceiver.EXTRA_CODE, code)
                    },
                )
            }
        }
        // پاسخ به سرشمارهٔ حرفی یا خط انبوه معنی ندارد.
        if (canReplyTo(raw.address) && !raw.isGroup) {
            val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
                .setLabel(context.getString(R.string.action_reply_hint))
                .build()
            val reply = NotificationCompat.Action.Builder(
                0,
                context.getString(R.string.action_reply),
                actionIntent(NotificationActionReceiver.ACTION_REPLY, id, message, mutable = true),
            )
                .addRemoteInput(input)
                .setAllowGeneratedReplies(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
            builder.addAction(reply)
        }
        builder.addAction(
            NotificationCompat.Action.Builder(
                0,
                context.getString(R.string.action_mark_read),
                actionIntent(NotificationActionReceiver.ACTION_MARK_READ, id, message),
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build(),
        )

        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    /** ارسال ناموفق هرگز بی‌صدا نیست. */
    @SuppressLint("MissingPermission")
    fun notifySendFailed(message: MessageEntity) {
        if (!canNotify()) return
        NotificationChannels.ensure(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.PERSONAL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notify_send_failed_title))
            .setContentText(context.getString(R.string.notify_send_failed_text, sender(message.address)))
            .setContentIntent(contentIntent(message.threadId))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(idOf(message.providerId) xor SEND_FAILED_SALT, notification)
        }
    }

    /**
     * پیامک زمان‌بندی‌شده‌ای که فرستاده نشد (ADR-0011): یا گوشی آن‌قدر خاموش
     * بوده که ارسال خودکار دیگر درست نبود، یا ثبت پیامک شکست خورد. متنش در صف
     * می‌ماند و در گفتگو با «الان بفرست» دیده می‌شود، پس گم نمی‌شود.
     */
    @SuppressLint("MissingPermission")
    fun notifyScheduleMissed(message: ScheduledMessageEntity) {
        if (!canNotify()) return
        NotificationChannels.ensure(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.PERSONAL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notify_schedule_missed_title))
            .setContentText(context.getString(R.string.notify_schedule_missed_text))
            .setContentIntent(contentIntent(message.threadId))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(idOf(message.id) xor SCHEDULE_MISSED_SALT, notification)
        }
    }

    /**
     * MMSی که دریافتش از MMSC ممکن نشد. اعلانش در provider هست و از داخل
     * گفتگو دوباره دریافت می‌شود، پس بی‌صدا گم نمی‌شود.
     */
    @SuppressLint("MissingPermission")
    fun notifyMmsProblem(from: String?, threadId: Long, saved: Boolean) {
        if (!canNotify()) return
        NotificationChannels.ensure(context)
        val text = context.getString(if (saved) R.string.notify_mms_failed else R.string.notify_mms_not_saved)
        val builder = NotificationCompat.Builder(context, NotificationChannels.PERSONAL)
            .setSmallIcon(R.drawable.ic_stat_asudeh)
            .setContentTitle(context.getString(R.string.notify_mms_title, sender(from.orEmpty())))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (threadId > 0) builder.setContentIntent(contentIntent(threadId))
        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), builder.build())
        }
    }

    /**
     * `Digest` (D40): «امروز ۱۲ تبلیغ و ۱ کلاهبرداری پنهان شد». بی‌صدا، و فقط
     * وقتی که چیزی پنهان شده باشد.
     */
    @SuppressLint("MissingPermission")
    fun notifyDigest(count: HiddenCount, frequency: DigestFrequency) {
        if (count.total == 0 || !canNotify()) return
        NotificationChannels.ensure(context)
        val resources = context.resources
        val parts = buildList {
            if (count.promo > 0) add(resources.getQuantityString(R.plurals.digest_promo, count.promo, count.promo))
            if (count.scam > 0) add(resources.getQuantityString(R.plurals.digest_scam, count.scam, count.scam))
        }
        val text = PersianText.persianDigits(
            if (parts.size == 2) context.getString(R.string.digest_join, parts[0], parts[1]) else parts.single(),
        )
        val title = context.getString(
            if (frequency == DigestFrequency.WEEKLY) R.string.digest_title_weekly else R.string.digest_title_daily,
        )
        val target = if (count.promo == 0) Folder.SCAM else Folder.PROMO
        val notification = NotificationCompat.Builder(context, NotificationChannels.DIGEST)
            .setSmallIcon(R.drawable.ic_stat_asudeh)
            .setContentTitle(title)
            .setContentText(text)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    DIGEST_ID,
                    openFolder(target),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(DIGEST_ID, notification) }
    }

    /** برداشتن اعلان یک گفتگو، وقتی کاربر آن را باز کرده یا از اعلان پاسخ داده است. */
    fun cancelThread(threadId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(threadNotificationId(threadId)) }
    }

    private fun actionIntent(
        action: String,
        notificationId: Int,
        message: ClassifiedMessage,
        mutable: Boolean = false,
        extras: Intent.() -> Unit = {},
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            // هر اعلان و هر دکمه یک `data` جدا دارد تا PendingIntentها روی هم نوشته نشوند.
            .setData(Uri.parse("asudeh-action://$action/${message.threadId}/${message.providerId}"))
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, message.threadId)
            .putExtra(NotificationActionReceiver.EXTRA_FOLDER, message.placement.folder.name)
            .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, message.raw.address)
            .putExtra(NotificationActionReceiver.EXTRA_SUBSCRIPTION_ID, message.raw.subscriptionId)
            .apply(extras)
        // پاسخ مستقیم باید mutable باشد تا سیستم متن کاربر را به آن اضافه کند.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else if (mutable) {
                0
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        return PendingIntent.getBroadcast(context, notificationId, intent, flags)
    }

    private fun canReplyTo(address: String): Boolean = when (Addresses.kindOf(address)) {
        SenderKind.MOBILE, SenderKind.OTHER_NUMBER -> true
        else -> false
    }

    private fun sender(address: String): String =
        PersianText.persianDigits(address.ifBlank { context.getString(R.string.notify_unknown_sender) })

    private fun contentIntent(threadId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        threadNotificationId(threadId),
        openConversation(threadId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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

    companion object {
        private const val MAX_PREVIEW = 200
        private const val SEND_FAILED_SALT = 0x5E1D
        private const val SCHEDULE_MISSED_SALT = 0x5C4E
        private const val THREAD_SALT = 0x7A11
        private const val DIGEST_ID = 0x0D16

        /** شناسهٔ اعلان از شناسهٔ ۶۴بیتی، بدون اینکه دو پیامک روی هم بیفتند. */
        private fun idOf(id: Long): Int = (id xor (id ushr 32)).toInt()

        fun threadNotificationId(threadId: Long): Int = idOf(threadId) xor THREAD_SALT
    }
}
