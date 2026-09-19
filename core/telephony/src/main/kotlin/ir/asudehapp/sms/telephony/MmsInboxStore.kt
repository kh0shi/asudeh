package ir.asudehapp.sms.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony

/**
 * نوشتن اعلان MMS در provider سیستم، همان‌طور که اپ‌های پیامک پیش‌فرض قبل از
 * دریافت خود پیام انجام می‌دهند (`m_type = 130`، یعنی `m-notification-ind`).
 * نشانی دریافت (`ct_l`) و انقضا نگه داشته می‌شود تا نسخهٔ بعدی آسوده، یا اپ
 * دیگری، بتواند پیام را از MMSC بگیرد.
 */
class MmsInboxStore(private val context: Context) {

    fun saveNotification(notification: MmsNotification, subscriptionId: Int, now: Long): Long {
        val from = notification.from?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, from))
            // زمان در جدول MMS بر حسب ثانیه است.
            put(Telephony.Mms.DATE, now / 1_000)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_NOTIFICATION_IND)
            put(Telephony.Mms.TRANSACTION_ID, notification.transactionId)
            put(Telephony.Mms.CONTENT_LOCATION, notification.contentLocation)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            notification.mmsVersion?.let { put(Telephony.Mms.MMS_VERSION, it) }
            notification.messageSize?.let { put(Telephony.Mms.MESSAGE_SIZE, it) }
            notification.expirySeconds?.let { put(Telephony.Mms.EXPIRY, it) }
            notification.subject?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
            }
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values)
            ?: error("نوشتن اعلان MMS در provider ناموفق بود")
        val id = ContentUris.parseId(uri)

        val address = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, from)
            put(Telephony.Mms.Addr.TYPE, ADDRESS_TYPE_FROM)
            put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
            put(Telephony.Mms.Addr.MSG_ID, id)
        }
        val addressUri = Telephony.Mms.CONTENT_URI.buildUpon()
            .appendPath(id.toString())
            .appendPath("addr")
            .build()
        context.contentResolver.insert(addressUri, address)
        return id
    }

    private companion object {
        const val MESSAGE_TYPE_NOTIFICATION_IND = 130
        const val ADDRESS_TYPE_FROM = 137
        const val CHARSET_UTF8 = 106
        const val UNKNOWN_SENDER = "insert-address-token"
    }
}
