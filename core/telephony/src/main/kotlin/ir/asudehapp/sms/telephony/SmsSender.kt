package ir.asudehapp.sms.telephony

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ارسال پیامک.
 *
 * `SaveFirst` برای ارسال هم برقرار است: پیامک **پیش از** ارسال در provider
 * (`OUTBOX`) و ایندکس نوشته می‌شود، و نتیجهٔ ارسال بعداً از [SmsSentReceiver]
 * می‌رسد. ارسال ناموفق هرگز بی‌صدا نیست: در گفتگو «ارسال نشد» دیده می‌شود و
 * اعلان هم دارد.
 */
class SmsSender(
    private val context: Context,
    private val repository: AsudehRepository,
) {

    /** خروجی، پیامک ثبت‌شده است. اگر نوشتن در provider ممکن نباشد خطا می‌دهد. */
    suspend fun send(
        address: String,
        body: String,
        subscriptionId: Int = -1,
        folder: Folder = Folder.INBOX,
    ): MessageEntity {
        val message = repository.recordOutgoing(address, body, subscriptionId, folder)
        transmit(message)
        return message
    }

    /** «دوباره بفرست» برای پیامکی که ارسالش ناموفق بود. */
    suspend fun resend(message: MessageEntity) {
        repository.setSendStatus(message.providerId, SendStatus.PENDING)
        transmit(message)
    }

    /**
     * `Unsub11`: ارسال «۱۱» به سرشمارهٔ تبلیغاتی، **همیشه با تأیید صریح کاربر**
     * و فقط برای خطوط `AdLine` (D28). پیامک ارسالی در همان گفتگو در
     * `PromoFolder` دیده می‌شود.
     */
    suspend fun unsubscribe(address: String, subscriptionId: Int = -1): MessageEntity {
        require(Addresses.isAdLine(address)) { "لغو۱۱ فقط برای خطوط انبوه معنی دارد" }
        return send(address, UNSUBSCRIBE_BODY, subscriptionId, Folder.PROMO)
    }

    // مجوز SEND_SMS برای اپ پیامک پیش‌فرض خودکار داده می‌شود.
    @SuppressLint("MissingPermission")
    private suspend fun transmit(message: MessageEntity) = withContext(Dispatchers.IO) {
        try {
            val manager = smsManager(message.subId)
            val parts = manager.divideMessage(message.body)
            val sentIntents = ArrayList(parts.indices.map { sentIntent(message.providerId, it) })
            // گزارش تحویل خاموش به‌طور پیش‌فرض است (D53)؛ فقط برای تکهٔ آخر خواسته می‌شود.
            val deliveryIntents = if (context.telephonyHost.settings.deliveryReports) {
                ArrayList(parts.indices.map { if (it == parts.lastIndex) deliveryIntent(message.providerId) else null })
            } else {
                null
            }
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(message.address, null, parts, sentIntents, deliveryIntents)
            } else {
                manager.sendTextMessage(message.address, null, message.body, sentIntents[0], deliveryIntents?.get(0))
            }
        } catch (failure: Exception) {
            // مثلاً اپ پیش‌فرض نیست، یا سیم‌کارت در دسترس نیست.
            repository.setSendStatus(message.providerId, SendStatus.FAILED)
            context.telephonyHost.notifier.notifySendFailed(message)
        }
    }

    private fun sentIntent(providerId: Long, part: Int): PendingIntent {
        // هر تکه یک `data` جدا دارد تا PendingIntentها روی هم نوشته نشوند.
        val intent = Intent(context, SmsSentReceiver::class.java)
            .setAction(SmsSentReceiver.ACTION_SENT)
            .setData(Uri.parse("asudeh-sent://sms/$providerId/$part"))
            .putExtra(SmsSentReceiver.EXTRA_PROVIDER_ID, providerId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun deliveryIntent(providerId: Long): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java)
            .setAction(SmsSentReceiver.ACTION_DELIVERED)
            .setData(Uri.parse("asudeh-delivered://sms/$providerId"))
            .putExtra(SmsSentReceiver.EXTRA_PROVIDER_ID, providerId)
        // گزارش تحویل PDU را در extra می‌آورد، پس PendingIntent باید mutable باشد.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun smsManager(subscriptionId: Int): SmsManager = smsManagerFor(context, subscriptionId)

    companion object {
        const val UNSUBSCRIBE_BODY: String = "11"

        /** `SmsManager` یک سیم‌کارت؛ روی اندروید ۸ تا ۱۱ هم درست گرفته می‌شود. */
        @Suppress("DEPRECATION")
        fun smsManagerFor(context: Context, subscriptionId: Int): SmsManager {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)
                    ?: error("SmsManager در دسترس نیست")
                return if (subscriptionId >= 0) base.createForSubscriptionId(subscriptionId) else base
            }
            // پیش از اندروید ۱۲، `getSystemService` برای SmsManager null برمی‌گرداند.
            return if (subscriptionId >= 0) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }
        }
    }
}
