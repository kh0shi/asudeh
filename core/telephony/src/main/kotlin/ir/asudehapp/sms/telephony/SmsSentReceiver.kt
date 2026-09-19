package ir.asudehapp.sms.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * نتیجهٔ ارسال هر تکهٔ پیامک. export نشده است؛ فقط PendingIntent خود اپ به آن
 * می‌رسد.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L)
        if (providerId <= 0) return
        val status = if (resultCode == Activity.RESULT_OK) SendStatus.SENT else SendStatus.FAILED

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

    companion object {
        const val ACTION_SENT = "ir.asudehapp.sms.SMS_SENT"
        const val EXTRA_PROVIDER_ID = "ir.asudehapp.sms.PROVIDER_ID"
        private const val TAG = "AsudehSmsSent"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
