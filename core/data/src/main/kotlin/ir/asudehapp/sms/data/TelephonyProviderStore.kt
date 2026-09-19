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

    /**
     * نوشتن پیامک ارسالی **پیش از** ارسال، در `OUTBOX` (`SaveFirst` برای ارسال).
     * نتیجهٔ ارسال بعداً با [setSendResult] ثبت می‌شود.
     */
    suspend fun saveOutgoing(
        address: String,
        body: String,
        subscriptionId: Int,
        now: Long,
    ): StoredMessage = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.DATE_SENT, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            ?: error("نوشتن پیامک ارسالی در Telephony Provider ناموفق بود")
        val providerId = ContentUris.parseId(uri)
        StoredMessage(providerId = providerId, threadId = threadIdOf(providerId))
    }

    /** ثبت نتیجهٔ ارسال: `SENT`، `FAILED` یا دوباره `OUTBOX` برای «دوباره بفرست». */
    suspend fun setSendResult(providerId: Long, status: SendStatus) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerId)
        if (status == SendStatus.DELIVERED) {
            val values = ContentValues().apply { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) }
            runCatching { context.contentResolver.update(uri, values, null, null) }
            return@withContext
        }
        val type = when (status) {
            SendStatus.SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
            SendStatus.FAILED -> Telephony.Sms.MESSAGE_TYPE_FAILED
            SendStatus.PENDING -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
            SendStatus.NONE, SendStatus.DELIVERED -> return@withContext
        }
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
        // «ناموفق» با رسیدن تکهٔ بعدیِ یک پیامک چندتکه «ارسال‌شده» نمی‌شود.
        val keepFailure = if (status == SendStatus.SENT) {
            "${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_FAILED}"
        } else {
            null
        }
        runCatching { context.contentResolver.update(uri, values, keepFailure, null) }
    }

    /** شناسهٔ گفتگوی یک سرشماره؛ اگر نباشد، ساخته می‌شود. */
    suspend fun threadIdFor(address: String): Long = withContext(Dispatchers.IO) {
        Telephony.Threads.getOrCreateThreadId(context, address)
    }

    private fun threadIdOf(providerId: Long): Long = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID),
        "${Telephony.Sms._ID} = ?",
        arrayOf(providerId.toString()),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L

    /**
     * شناسهٔ همهٔ پیامک‌های provider (به‌جز پیش‌نویس‌ها)، برای همگام‌سازی (D21).
     * اگر خواندن ممکن نباشد خطا می‌دهد، نه فهرست خالی؛ فهرست خالی یعنی «همهٔ
     * پیامک‌ها پاک شده‌اند».
     */
    suspend fun readIds(): List<Long> = withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.TYPE} != ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()),
            null,
        ) ?: error("Telephony Provider در دسترس نیست")
        cursor.use {
            val out = ArrayList<Long>(it.count)
            while (it.moveToNext()) out += it.getLong(0)
            out
        }
    }

    /** خواندن پیامک‌ها با شناسه، دسته‌دسته تا از سقف آرگومان‌های SQLite رد نشویم. */
    suspend fun readByIds(ids: List<Long>): List<ProviderSms> = withContext(Dispatchers.IO) {
        val out = ArrayList<ProviderSms>(ids.size)
        for (chunk in ids.chunked(CHUNK)) {
            val placeholders = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                "${Telephony.Sms._ID} IN ($placeholders)",
                chunk.map(Long::toString).toTypedArray(),
                "${Telephony.Sms._ID} ASC",
            )?.use { cursor -> out += cursor.readMessages() }
        }
        out
    }

    private fun Cursor.readMessages(): List<ProviderSms> {
        val out = ArrayList<ProviderSms>(count)
        while (moveToNext()) {
            val type = getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE))
            if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
            out += ProviderSms(
                id = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
                threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
                address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: UNKNOWN_SENDER,
                body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                date = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE)),
                dateSent = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)),
                read = getInt(getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
                // هر نوعی جز «دریافتی» پیامک خود کاربر است: ارسالی، صف، ناموفق.
                outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                sendStatus = sendStatusOf(type, getInt(getColumnIndexOrThrow(Telephony.Sms.STATUS))),
                subId = getInt(getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)),
            )
        }
        return out
    }

    private fun sendStatusOf(type: Int, status: Int): SendStatus = when (type) {
        Telephony.Sms.MESSAGE_TYPE_SENT ->
            if (status == Telephony.Sms.STATUS_COMPLETE) SendStatus.DELIVERED else SendStatus.SENT
        Telephony.Sms.MESSAGE_TYPE_FAILED -> SendStatus.FAILED
        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> SendStatus.PENDING
        else -> SendStatus.NONE
    }

    /**
     * «خوانده‌شده» یا «خوانده‌نشده» در سیستم، تا با ایندکس محلی یکی بماند. فقط
     * اپ پیش‌فرض می‌تواند در provider بنویسد؛ خطا نادیده گرفته می‌شود، چون
     * ایندکس محلی همچنان درست است.
     */
    suspend fun markRead(keys: List<MessageKey>, read: Boolean) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext
        val flag = if (read) 1 else 0
        for (key in keys) {
            val base = if (key.kind == MessageEntity.KIND_MMS) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, flag)
                if (read) put(Telephony.Sms.SEEN, 1)
            }
            val uri: Uri = ContentUris.withAppendedId(base, key.providerId)
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    /**
     * حذف از provider. فقط با اقدام صریح کاربر صدا زده می‌شود (ADR-0003 بند ۳).
     * خروجی، شناسه‌هایی است که واقعاً حذف شدند.
     */
    suspend fun delete(providerIds: List<Long>): List<Long> = withContext(Dispatchers.IO) {
        providerIds.filter { id ->
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
            runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
    }

    companion object {
        /** برخلاف Fossify، پیامک بدون آدرس فرستنده دور ریخته نمی‌شود (D24). */
        const val UNKNOWN_SENDER: String = "نامشخص"

        private const val CHUNK = 500

        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.STATUS,
        )
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
    val sendStatus: SendStatus,
    val subId: Int,
)
