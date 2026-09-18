package ir.asudehapp.sms.telephony

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import ir.asudehapp.sms.model.Addresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ارسال پیامک، و نوشتن نسخهٔ ارسالی در Telephony Provider سیستم. */
class SmsSender(private val context: Context) {

    suspend fun send(address: String, body: String, subscriptionId: Int = -1) =
        withContext(Dispatchers.IO) {
            val manager = smsManager(subscriptionId)
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                manager.sendTextMessage(address, null, body, null, null)
            }
            writeSent(address, body, subscriptionId)
        }

    /**
     * `Unsub11`: ارسال «۱۱» به سرشمارهٔ تبلیغاتی، **همیشه با تأیید صریح کاربر**
     * و فقط برای خطوط `AdLine` (D28). تصمیم نمایش دکمه با رابط کاربری است؛
     * این تابع فقط همان یک شرط ساختاری را دوباره بررسی می‌کند.
     */
    suspend fun unsubscribe(address: String, subscriptionId: Int = -1) {
        require(Addresses.isAdLine(address)) { "لغو۱۱ فقط برای خطوط انبوه معنی دارد" }
        send(address, UNSUBSCRIBE_BODY, subscriptionId)
    }

    private fun smsManager(subscriptionId: Int): SmsManager {
        val base = context.getSystemService(SmsManager::class.java)
        return if (subscriptionId >= 0) base.createForSubscriptionId(subscriptionId) else base
    }

    private fun writeSent(address: String, body: String, subscriptionId: Int) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) }
    }

    companion object {
        const val UNSUBSCRIBE_BODY: String = "11"
    }
}
