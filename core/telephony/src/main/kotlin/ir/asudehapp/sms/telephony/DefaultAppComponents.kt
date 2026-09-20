package ir.asudehapp.sms.telephony

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import ir.asudehapp.sms.model.SmsUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * اندروید برای اینکه یک اپ بتواند اپ پیامک پیش‌فرض شود، وجود این چهار جزء را
 * لازم می‌داند: گیرندهٔ `SMS_DELIVER`، گیرندهٔ `WAP_PUSH_DELIVER`، سرویس
 * «پاسخ با پیامک» و یک Activity برای `sendto`.
 */

/**
 * «پاسخ با پیامک» هنگام رد تماس. متن را خود سیستم می‌دهد و کاربر منتظر است که
 * ارسال شود؛ پس این سرویس واقعاً می‌فرستد، و اگر نتواند، اعلان «ارسال نشد»
 * نشان داده می‌شود.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val recipients = intent?.dataString?.let(SmsUri::parse)?.recipients.orEmpty()
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE ||
            text.isBlank() || recipients.isEmpty()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val host = telephonyHost
        scope.launch {
            try {
                for (recipient in recipients) {
                    runCatching { host.smsSender.send(recipient, text) }
                        .onFailure { Log.e(TAG, "پاسخ با پیامک ارسال نشد", it) }
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "AsudehRespond"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * رسیدن اعلان MMS (D26). ترتیب همان `SaveFirst` است: اول اعلان در provider و
 * ایندکس نوشته می‌شود، بعد دریافت خود پیام از سیستم خواسته می‌شود. اگر دریافت
 * شکست بخورد، پیام در گفتگو با دکمهٔ «دریافت» می‌ماند و بی‌صدا گم نمی‌شود.
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val subscriptionId = intent.getIntExtra("subscription", -1)
        val host = context.telephonyHost
        val applicationContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val notification = MmsNotification.parse(pdu, nowSeconds = now / 1_000) ?: return@launch
                val mms = host.repository.mms
                // اعلان تکراری: همان پیام قبلاً ثبت شده است.
                if (mms.findByTransaction(notification.transactionId) != null) return@launch

                val stored = runCatching {
                    mms.saveNotification(
                        from = notification.from,
                        transactionId = notification.transactionId,
                        contentLocation = notification.contentLocation,
                        subject = notification.subject,
                        messageSize = notification.messageSize,
                        expirySeconds = notification.expirySeconds,
                        mmsVersion = notification.mmsVersion,
                        subscriptionId = subscriptionId,
                        now = now,
                    )
                }.onFailure { Log.e(TAG, "نوشتن اعلان MMS ناموفق بود", it) }.getOrNull()
                if (stored == null) {
                    host.notifier.notifyMmsProblem(notification.from, 0L, saved = false)
                    return@launch
                }
                runCatching {
                    host.repository.recordMmsNotification(
                        stored,
                        notification.from.orEmpty(),
                        notification.subject,
                        subscriptionId,
                        now,
                    )
                }.onFailure { Log.w(TAG, "ایندکس اعلان MMS ناموفق بود", it) }

                // دریافت خودکار را کاربر می‌تواند در تنظیمات خاموش کند. خاموش که
                // باشد، پیام در گفتگو با دکمهٔ «دریافت» می‌ماند و گم نمی‌شود.
                if (!host.settings.mmsAutoDownload) return@launch
                runCatching {
                    MmsDownloader.download(applicationContext, stored.providerId, notification.contentLocation, subscriptionId)
                }.onFailure {
                    Log.w(TAG, "درخواست دریافت MMS ممکن نشد", it)
                    host.notifier.notifyMmsProblem(notification.from, stored.threadId, saved = true)
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "خطا در دریافت MMS", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AsudehMms"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
