package ir.asudehapp.sms.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import ir.asudehapp.sms.classifier.receive.RawSms
import ir.asudehapp.sms.classifier.receive.StoredMessage
import ir.asudehapp.sms.classifier.receive.TelephonyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * نوشتن و خواندن Telephony Provider سیستم، که منبع حقیقت است (D19 ب).
 *
 * این کلاس عمداً هیچ منطق فیلتری ندارد: هر پیامکی که به آن برسد نوشته می‌شود
 * (`SaveFirst`، ADR-0003). برخلاف Fossify، پیامک بدون آدرس فرستنده هم دور
 * ریخته نمی‌شود و هم زمان ارسال و هم زمان دریافت نگه داشته می‌شوند (D24).
 */
class TelephonyProviderStore(private val context: Context) : TelephonyStore {

    override suspend fun saveIncoming(raw: RawSms): StoredMessage = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, raw.address.ifBlank { UNKNOWN_SENDER })
            put(Telephony.Sms.BODY, raw.body)
            put(Telephony.Sms.DATE, raw.receivedAt)
            put(Telephony.Sms.DATE_SENT, raw.sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (raw.subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, raw.subscriptionId)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            ?: error("نوشتن پیامک در Telephony Provider ناموفق بود")
        val providerId = ContentUris.parseId(uri)
        StoredMessage(providerId = providerId, threadId = threadIdOf(providerId))
    }

    private fun threadIdOf(providerId: Long): Long = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID),
        "${Telephony.Sms._ID} = ?",
        arrayOf(providerId.toString()),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L

    /**
     * خواندن پیامک‌های موجود، برای `HistorySweep` و همگام‌سازی افزایشی (D21).
     * `sinceId` صفر یعنی بازسازی کامل.
     */
    suspend fun readAll(sinceId: Long = 0): List<ProviderSms> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID,
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms._ID} > ?",
            arrayOf(sinceId.toString()),
            "${Telephony.Sms._ID} ASC",
        )?.use { cursor -> cursor.readMessages() } ?: emptyList()
    }

    private fun Cursor.readMessages(): List<ProviderSms> {
        val out = ArrayList<ProviderSms>(count)
        while (moveToNext()) {
            val type = getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE))
            out += ProviderSms(
                id = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
                threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
                address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: UNKNOWN_SENDER,
                body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                date = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE)),
                dateSent = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)),
                read = getInt(getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
                outgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                subId = getInt(getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)),
            )
        }
        return out
    }

    /** خواندن پیامک به‌عنوان «خوانده‌شده» در سیستم، تا با ایندکس محلی یکی بماند. */
    suspend fun markRead(providerIds: List<Long>) = withContext(Dispatchers.IO) {
        if (providerIds.isEmpty()) return@withContext
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        for (id in providerIds) {
            val uri: Uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    companion object {
        /** برخلاف Fossify، پیامک بدون آدرس فرستنده دور ریخته نمی‌شود (D24). */
        const val UNKNOWN_SENDER: String = "نامشخص"
    }
}

data class ProviderSms(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val read: Boolean,
    val outgoing: Boolean,
    val subId: Int,
)
