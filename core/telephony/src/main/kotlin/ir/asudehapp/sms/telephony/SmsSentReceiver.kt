package ir.asudehapp.sms.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * نتیجهٔ ارسال هر تکهٔ پیامک، و گزارش تحویل. export نشده است؛ فقط
 * PendingIntent خود اپ به آن می‌رسد.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L)
        if (providerId <= 0) return
        val status = when (intent.action) {
            ACTION_SENT -> if (resultCode == Activity.RESULT_OK) SendStatus.SENT else SendStatus.FAILED
            // گزارش تحویل ناموفق چیزی را عوض نمی‌کند: پیامک فرستاده شده است.
            ACTION_DELIVERED -> if (isDelivered(intent)) SendStatus.DELIVERED else return
            else -> return
        }

        val host = context.telephonyHost
        val pending = goAsync()
        scope.launch {
            try {
                host.repository.setSendStatus(providerId, status)
                if (status == SendStatus.FAILED) {
                    host.repository.findSms(providerId)?.let { message: MessageEntity ->
                        host.notifier.notifySendFailed(message)
                    }
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "ثبت نتیجهٔ ارسال ناموفق بود", failure)
            } finally {
                pending.finish()
            }
        }
    }

    /** وضعیت GSM در گزارش تحویل: ۰ تا ۳۱ یعنی تحویل شد (3GPP TS 23.040 §9.2.3.15). */
    private fun isDelivered(intent: Intent): Boolean {
        val pdu = intent.getByteArrayExtra("pdu") ?: return false
        val format = intent.getStringExtra("format") ?: SmsMessage.FORMAT_3GPP
        val report = runCatching { SmsMessage.createFromPdu(pdu, format) }.getOrNull() ?: return false
        val status = report.status
        return if (format == SmsMessage.FORMAT_3GPP2) status == 0 else status in 0..0x1F
    }

    companion object {
        const val ACTION_SENT = "ir.asudehapp.sms.SMS_SENT"
        const val ACTION_DELIVERED = "ir.asudehapp.sms.SMS_DELIVERED"
        const val EXTRA_PROVIDER_ID = "ir.asudehapp.sms.PROVIDER_ID"
        private const val TAG = "AsudehSmsSent"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
