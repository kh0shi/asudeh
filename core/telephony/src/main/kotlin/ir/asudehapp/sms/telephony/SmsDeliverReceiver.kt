package ir.asudehapp.sms.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import ir.asudehapp.sms.classifier.receive.RawSms
import ir.asudehapp.sms.classifier.receive.ReceivePipeline
import ir.asudehapp.sms.data.AsudehRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * دریافت پیامک به‌عنوان اپ پیش‌فرض (`SMS_DELIVER`).
 *
 * برخلاف `SmsReceiver` فسیفای، اینجا **هیچ منطقی پیش از ذخیره اجرا نمی‌شود**
 * (ADR-0003، D24):
 * - پیامک بدون آدرس فرستنده دور ریخته نمی‌شود، بلکه با برچسب «نامشخص» ذخیره می‌شود.
 * - هیچ کلمهٔ کلیدی یا شمارهٔ مسدودی پیش از ذخیره پیامک را دور نمی‌ریزد.
 * - هم زمان ارسال (از PDU) و هم زمان دریافت نگه داشته می‌شوند.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            ?: return
        if (parts.isEmpty()) return

        val raw = parts.toRawSms(intent)
        val pending = goAsync()

        // `goAsync` به ما چند ثانیه وقت می‌دهد؛ `ReceivePipeline` خودش سقف زمانی
        // طبقه‌بندی را رعایت می‌کند (D23).
        runCatching {
            runBlocking {
                withContext(Dispatchers.Default) {
                    pipelineFor(context).onReceive(raw)
                }
            }
        }.onFailure { Log.e(TAG, "خطا در مسیر دریافت پیامک", it) }

        pending.finish()
    }

    private fun pipelineFor(context: Context): ReceivePipeline {
        val repository = AsudehRepository(context.applicationContext)
        return ReceivePipeline(
            store = repository.store,
            index = repository.index,
            notifier = AsudehNotifier(context.applicationContext) { threadId ->
                conversationIntent(context.applicationContext, threadId)
            },
            classify = repository.classifier::classify,
            isKnownContact = { address -> Contacts.isKnown(context.applicationContext, address) },
            userRules = { repository.currentUserRules() },
            onError = { stage, error -> Log.w(TAG, "مرحلهٔ $stage شکست خورد", error) },
        )
    }

    private fun List<SmsMessage>.toRawSms(intent: Intent): RawSms {
        val head = first()
        val body = joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val subscriptionId = intent.getIntExtra("subscription", -1)
        return RawSms(
            address = head.displayOriginatingAddress.orEmpty(),
            body = body,
            // زمان ارسال از PDU می‌آید، نه `System.currentTimeMillis()` (D24).
            sentAt = head.timestampMillis,
            receivedAt = System.currentTimeMillis(),
            subscriptionId = subscriptionId,
        )
    }

    companion object {
        private const val TAG = "AsudehSmsReceiver"

        /**
         * توسط `:app` پر می‌شود تا لمس اعلان همان گفتگو را باز کند، بدون اینکه
         * این ماژول به کلاس‌های `:app` وابسته شود.
         */
        var conversationIntent: (Context, Long) -> Intent? = { _, _ -> null }
    }
}
