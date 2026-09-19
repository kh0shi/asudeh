package ir.asudehapp.sms.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import ir.asudehapp.sms.mms.IncomingMms
import ir.asudehapp.sms.mms.MmsPart
import ir.asudehapp.sms.model.Addresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** یک MMS ذخیره‌شده در provider. */
data class StoredMms(
    val providerId: Long,
    val threadId: Long,
    /** طرف‌های دیگر گفتگو، بدون خود کاربر (تا جایی که شماره‌اش معلوم است). */
    val participants: List<String>,
)

/** یک part از MMS در provider، برای نمایش در گفتگو. */
data class MmsPartInfo(
    val partId: Long,
    val contentType: String,
    val name: String?,
    val text: String?,
) {
    val isImage: Boolean get() = contentType.startsWith("image/")
    val isAttachment: Boolean get() = contentType != TEXT_PLAIN && contentType != SMIL

    private companion object {
        const val TEXT_PLAIN = "text/plain"
        const val SMIL = "application/smil"
    }
}

/** یک MMS خوانده‌شده از provider، برای همگام‌سازی. */
data class ProviderMms(
    val id: Long,
    val threadId: Long,
    val address: String,
    val recipients: List<String>,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val read: Boolean,
    val outgoing: Boolean,
    val sendStatus: SendStatus,
    val subId: Int,
    val attachments: Int,
    /** فقط اعلان رسیده و خود پیام دریافت نشده است (`m-notification-ind`). */
    val pendingDownload: Boolean,
)

/**
 * نوشتن و خواندن MMS در Telephony Provider سیستم (D26). منبع حقیقت همین‌جاست،
 * مثل پیامک (D19 ب). ترتیب کار همان `SaveFirst` است: اعلان MMS پیش از دریافت
 * نوشته می‌شود، و MMS ارسالی پیش از ارسال.
 */
class MmsProviderStore(private val context: Context) {

    private val resolver get() = context.contentResolver

    /**
     * اعلان MMS (`m_type = 130`). نشانی دریافت (`ct_l`) نگه داشته می‌شود تا اگر
     * دریافت شکست خورد، از داخل گفتگو دوباره امتحان شود.
     */
    suspend fun saveNotification(
        from: String?,
        transactionId: String,
        contentLocation: String,
        subject: String?,
        messageSize: Long?,
        expirySeconds: Long?,
        mmsVersion: Int?,
        subscriptionId: Int,
        now: Long,
    ): StoredMms = withContext(Dispatchers.IO) {
        val sender = from?.takeIf { it.isNotBlank() } ?: INSERT_ADDRESS_TOKEN
        val threadId = Telephony.Threads.getOrCreateThreadId(context, sender)
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            // زمان در جدول MMS بر حسب ثانیه است.
            put(Telephony.Mms.DATE, now / 1_000)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.MESSAGE_TYPE, TYPE_NOTIFICATION_IND)
            put(Telephony.Mms.TRANSACTION_ID, transactionId)
            put(Telephony.Mms.CONTENT_LOCATION, contentLocation)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            mmsVersion?.let { put(Telephony.Mms.MMS_VERSION, it) }
            messageSize?.let { put(Telephony.Mms.MESSAGE_SIZE, it) }
            expirySeconds?.let { put(Telephony.Mms.EXPIRY, it) }
            subject?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
            }
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values)
            ?: error("نوشتن اعلان MMS در provider ناموفق بود")
        val id = ContentUris.parseId(uri)
        insertAddress(id, sender, ADDRESS_FROM)
        StoredMms(id, threadId, listOf(sender))
    }

    /** اعلان تکراری (`WAP_PUSH` گاهی دو بار می‌رسد) دوباره ذخیره نمی‌شود. */
    suspend fun findByTransaction(transactionId: String): Long? = withContext(Dispatchers.IO) {
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.TRANSACTION_ID} = ? AND ${Telephony.Mms.MESSAGE_BOX} = ?",
            arrayOf(transactionId, Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /** نشانی دریافت یک اعلان MMS، برای «دوباره دریافت کن». */
    suspend fun downloadInfo(providerId: Long): DownloadInfo? = withContext(Dispatchers.IO) {
        resolver.query(
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId),
            arrayOf(Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.TRANSACTION_ID, Telephony.Mms.SUBSCRIPTION_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val location = cursor.getString(0) ?: return@use null
            DownloadInfo(location, cursor.getString(1).orEmpty(), cursor.getInt(2))
        }
    }

    /**
     * پیام دریافت‌شده **جای همان ردیف اعلان** نوشته می‌شود؛ هیچ ردیفی حذف
     * نمی‌شود و شناسهٔ پیامک در ایندکس عوض نمی‌شود. برای گفتگوی گروهی، پیام به
     * گفتگوی همهٔ اعضا منتقل می‌شود.
     */
    suspend fun completeDownload(
        providerId: Long,
        message: IncomingMms,
        ownNumbers: Set<String>,
    ): StoredMms = withContext(Dispatchers.IO) {
        val from = message.from?.takeIf { it.isNotBlank() } ?: INSERT_ADDRESS_TOKEN
        val participants = MmsParticipants.of(from, message.to + message.cc, ownNumbers)
        val threadId = if (participants.size > 1) {
            Telephony.Threads.getOrCreateThreadId(context, participants.toSet())
        } else {
            Telephony.Threads.getOrCreateThreadId(context, from)
        }

        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId)
        // اول partها، بعد نوع پیام؛ اگر کار نیمه‌کاره بماند، ردیف هنوز «اعلان» است
        // و دوباره دریافت می‌شود.
        for (part in message.parts) insertPart(providerId, part)
        for (address in message.to) insertAddress(providerId, address, ADDRESS_TO)
        for (address in message.cc) insertAddress(providerId, address, ADDRESS_CC)

        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.MESSAGE_TYPE, TYPE_RETRIEVE_CONF)
            if (message.dateSeconds > 0) put(Telephony.Mms.DATE_SENT, message.dateSeconds)
            message.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            message.contentType?.let { put(Telephony.Mms.CONTENT_TYPE, it) }
            message.messageClass?.let { put(Telephony.Mms.MESSAGE_CLASS, it) }
            if (message.mmsVersion != 0) put(Telephony.Mms.MMS_VERSION, message.mmsVersion)
            message.subject?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
            }
            put(Telephony.Mms.MESSAGE_SIZE, message.parts.sumOf { it.data.size })
        }
        if (resolver.update(uri, values, null, null) == 0) error("به‌روزرسانی MMS در provider ناموفق بود")
        StoredMms(providerId, threadId, participants)
    }

    /**
     * MMS ارسالی **پیش از ارسال** در `OUTBOX` نوشته می‌شود (`SaveFirst`). نتیجه
     * بعداً با [setBox] ثبت می‌شود.
     */
    suspend fun saveOutgoing(
        recipients: List<String>,
        text: String?,
        attachments: List<MmsPart>,
        subscriptionId: Int,
        now: Long,
    ): StoredMms = withContext(Dispatchers.IO) {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now / 1_000)
            put(Telephony.Mms.DATE_SENT, now / 1_000)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.MESSAGE_TYPE, TYPE_SEND_REQ)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
            put(Telephony.Mms.MESSAGE_SIZE, attachments.sumOf { it.data.size } + (text?.length ?: 0))
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values)
            ?: error("نوشتن MMS ارسالی در provider ناموفق بود")
        val id = ContentUris.parseId(uri)
        text?.takeIf { it.isNotEmpty() }?.let {
            insertPart(id, MmsPart("text/plain", it.toByteArray(Charsets.UTF_8), name = "text.txt"))
        }
        attachments.forEach { insertPart(id, it) }
        insertAddress(id, INSERT_ADDRESS_TOKEN, ADDRESS_FROM)
        recipients.forEach { insertAddress(id, it, ADDRESS_TO) }
        StoredMms(id, threadId, recipients)
    }

    /** `SENT`، `FAILED` یا دوباره `OUTBOX` برای «دوباره بفرست». */
    suspend fun setBox(providerId: Long, status: SendStatus, messageId: String? = null) =
        withContext(Dispatchers.IO) {
            val box = when (status) {
                SendStatus.SENT, SendStatus.DELIVERED -> Telephony.Mms.MESSAGE_BOX_SENT
                SendStatus.FAILED -> Telephony.Mms.MESSAGE_BOX_FAILED
                SendStatus.PENDING -> Telephony.Mms.MESSAGE_BOX_OUTBOX
                SendStatus.NONE -> return@withContext
            }
            val values = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, box)
                messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            }
            runCatching {
                resolver.update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId), values, null, null)
            }
        }

    /** partهای یک MMS، به ترتیب، برای نمایش در گفتگو. */
    suspend fun parts(providerId: Long): List<MmsPartInfo> = withContext(Dispatchers.IO) {
        resolver.query(
            partsUri(providerId),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME,
                Telephony.Mms.Part.CONTENT_LOCATION, Telephony.Mms.Part.TEXT),
            null,
            null,
            "${Telephony.Mms.Part.SEQ} ASC, ${Telephony.Mms.Part._ID} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MmsPartInfo(
                            partId = cursor.getLong(0),
                            contentType = cursor.getString(1)?.lowercase().orEmpty(),
                            name = cursor.getString(2) ?: cursor.getString(3),
                            text = cursor.getString(4),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** داده‌ی partها، برای ساختن دوبارهٔ PDU در «دوباره بفرست». */
    suspend fun attachmentData(providerId: Long): List<MmsPart> = withContext(Dispatchers.IO) {
        parts(providerId).filter { it.isAttachment }.mapNotNull { part ->
            val data = runCatching {
                resolver.openInputStream(partUri(part.partId))?.use { it.readBytes() }
            }.getOrNull() ?: return@mapNotNull null
            MmsPart(part.contentType, data, name = part.name)
        }
    }

    fun partUri(partId: Long): Uri = ContentUris.withAppendedId(PART_URI, partId)

    /**
     * حذف از provider. فقط با اقدام صریح کاربر صدا زده می‌شود (ADR-0003 بند ۳).
     * خروجی، شناسه‌هایی است که واقعاً حذف شدند.
     */
    suspend fun delete(providerIds: List<Long>): List<Long> = withContext(Dispatchers.IO) {
        providerIds.filter { id ->
            val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
            runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
    }

    /**
     * شناسهٔ همهٔ MMSهای provider (به‌جز پیش‌نویس‌ها و گزارش‌های تحویل)، برای
     * همگام‌سازی (D21). اگر خواندن ممکن نباشد خطا می‌دهد، نه فهرست خالی.
     */
    suspend fun readIds(): List<Long> = withContext(Dispatchers.IO) {
        val cursor = resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.MESSAGE_BOX} != ? AND ${Telephony.Mms.MESSAGE_TYPE} IN ($TYPE_SEND_REQ, $TYPE_NOTIFICATION_IND, $TYPE_RETRIEVE_CONF)",
            arrayOf(Telephony.Mms.MESSAGE_BOX_DRAFTS.toString()),
            null,
        ) ?: error("Telephony Provider در دسترس نیست")
        cursor.use {
            val out = ArrayList<Long>(it.count)
            while (it.moveToNext()) out += it.getLong(0)
            out
        }
    }

    /**
     * خواندن MMSها با شناسه. طرف‌های هر گفتگو از خود provider می‌آیند
     * ([threadRecipients])، تا شمارهٔ خود کاربر عضو گروه شمرده نشود.
     */
    suspend fun readByIds(ids: List<Long>, threadRecipients: Map<Long, List<String>>): List<ProviderMms> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<ProviderMms>(ids.size)
            for (chunk in ids.chunked(CHUNK)) {
                val placeholders = chunk.joinToString(",") { "?" }
                resolver.query(
                    Telephony.Mms.CONTENT_URI,
                    PROJECTION,
                    "${Telephony.Mms._ID} IN ($placeholders)",
                    chunk.map(Long::toString).toTypedArray(),
                    "${Telephony.Mms._ID} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) out += cursor.readMms(threadRecipients)
                }
            }
            out
        }

    private fun Cursor.readMms(threadRecipients: Map<Long, List<String>>): ProviderMms {
        val id = getLong(getColumnIndexOrThrow(Telephony.Mms._ID))
        val threadId = getLong(getColumnIndexOrThrow(Telephony.Mms.THREAD_ID))
        val box = getInt(getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
        val type = getInt(getColumnIndexOrThrow(Telephony.Mms.MESSAGE_TYPE))
        val outgoing = box != Telephony.Mms.MESSAGE_BOX_INBOX
        val subject = getString(getColumnIndexOrThrow(Telephony.Mms.SUBJECT))
        val parts = runCatching { partsBlocking(id) }.getOrDefault(emptyList())
        val text = parts.mapNotNull { it.text?.takeIf { _ -> it.contentType == "text/plain" } }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val addresses = runCatching { addressesBlocking(id) }.getOrDefault(emptyList())
        val from = addresses.firstOrNull { it.second == ADDRESS_FROM }?.first
        val members = threadRecipients[threadId].orEmpty()
        val address = when {
            !outgoing && from != null && from != INSERT_ADDRESS_TOKEN -> from
            members.isNotEmpty() -> members.first()
            else -> addresses.firstOrNull { it.second == ADDRESS_TO }?.first ?: UNKNOWN
        }
        return ProviderMms(
            id = id,
            threadId = threadId,
            address = address,
            recipients = if (members.size > 1) members else emptyList(),
            body = text.ifEmpty { subject.orEmpty() },
            // زمان در جدول MMS بر حسب ثانیه است.
            date = getLong(getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1_000,
            dateSent = getLong(getColumnIndexOrThrow(Telephony.Mms.DATE_SENT)) * 1_000,
            read = getInt(getColumnIndexOrThrow(Telephony.Mms.READ)) == 1,
            outgoing = outgoing,
            sendStatus = when (box) {
                Telephony.Mms.MESSAGE_BOX_SENT -> SendStatus.SENT
                Telephony.Mms.MESSAGE_BOX_OUTBOX -> SendStatus.PENDING
                Telephony.Mms.MESSAGE_BOX_FAILED -> SendStatus.FAILED
                else -> SendStatus.NONE
            },
            subId = getInt(getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID)),
            attachments = parts.count { it.isAttachment },
            pendingDownload = type == TYPE_NOTIFICATION_IND,
        )
    }

    private fun partsBlocking(id: Long): List<MmsPartInfo> = resolver.query(
        partsUri(id),
        arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.TEXT),
        null,
        null,
        null,
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(MmsPartInfo(cursor.getLong(0), cursor.getString(1)?.lowercase().orEmpty(), cursor.getString(2), cursor.getString(3)))
            }
        }
    }.orEmpty()

    private fun addressesBlocking(id: Long): List<Pair<String, Int>> = resolver.query(
        addressUri(id),
        arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
        null,
        null,
        null,
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                add(address to cursor.getInt(1))
            }
        }
    }.orEmpty()

    /**
     * طرف‌های هر گفتگو، همان‌طور که provider نگه می‌دارد. provider شمارهٔ خود
     * کاربر را جزو طرف‌ها نمی‌شمارد، پس این بهترین منبع برای گروه است.
     */
    suspend fun threadRecipients(): Map<Long, List<String>> = withContext(Dispatchers.IO) {
        val canonical = HashMap<Long, String>()
        runCatching {
            resolver.query(CANONICAL_URI, arrayOf("_id", "address"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) canonical[cursor.getLong(0)] = cursor.getString(1).orEmpty()
            }
        }
        val out = HashMap<Long, List<String>>()
        runCatching {
            resolver.query(THREADS_URI, arrayOf("_id", "recipient_ids"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val ids = cursor.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    out[cursor.getLong(0)] = ids.mapNotNull { canonical[it] }.filter { it.isNotBlank() }
                }
            }
        }
        out
    }

    private fun insertPart(messageId: Long, part: MmsPart) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.MSG_ID, messageId)
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            part.name?.let {
                put(Telephony.Mms.Part.NAME, it)
                put(Telephony.Mms.Part.CONTENT_LOCATION, it)
                put(Telephony.Mms.Part.FILENAME, it)
            }
            part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, it) }
            if (part.isText || part.isSmil) {
                put(Telephony.Mms.Part.CHARSET, CHARSET_UTF8)
                put(Telephony.Mms.Part.TEXT, part.text() ?: String(part.data, Charsets.UTF_8))
            }
        }
        val uri = resolver.insert(partsUri(messageId), values) ?: throw IOException("نوشتن part ناموفق بود")
        if (!part.isText && !part.isSmil) {
            resolver.openOutputStream(uri)?.use { it.write(part.data) }
                ?: throw IOException("نوشتن دادهٔ part ناموفق بود")
        }
    }

    private fun insertAddress(messageId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
            put(Telephony.Mms.Addr.MSG_ID, messageId)
        }
        resolver.insert(addressUri(messageId), values)
    }

    private fun partsUri(id: Long): Uri = Telephony.Mms.CONTENT_URI.buildUpon()
        .appendPath(id.toString()).appendPath("part").build()

    private fun addressUri(id: Long): Uri = Telephony.Mms.CONTENT_URI.buildUpon()
        .appendPath(id.toString()).appendPath("addr").build()

    data class DownloadInfo(val contentLocation: String, val transactionId: String, val subscriptionId: Int)

    companion object {
        const val TYPE_SEND_REQ = 128
        const val TYPE_NOTIFICATION_IND = 130
        const val TYPE_RETRIEVE_CONF = 132
        const val ADDRESS_FROM = 137
        const val ADDRESS_TO = 151
        const val ADDRESS_CC = 130
        const val CHARSET_UTF8 = 106
        const val MMS_VERSION_1_2 = 0x12
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
        private const val UNKNOWN = TelephonyProviderStore.UNKNOWN_SENDER
        private const val CHUNK = 200

        private val PART_URI: Uri = Uri.parse("content://mms/part")
        private val CANONICAL_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
        private val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")

        private val PROJECTION = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.DATE_SENT,
            Telephony.Mms.READ,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.MESSAGE_TYPE,
            Telephony.Mms.SUBSCRIPTION_ID,
            Telephony.Mms.SUBJECT,
        )
    }
}

/** تشخیص گفتگوی گروهی MMS؛ تابع خالص تا بدون اندروید آزمون شود. */
object MmsParticipants {

    /**
     * طرف‌های دیگر یک MMS دریافتی، با خود فرستنده در ابتدا. شمارهٔ خود کاربر
     * اگر معلوم باشد کنار گذاشته می‌شود. اگر معلوم نباشد و فقط یک گیرنده باشد،
     * آن گیرنده خود کاربر است و گفتگو دونفره است.
     */
    fun of(from: String, recipients: List<String>, ownNumbers: Set<String>): List<String> {
        val own = ownNumbers.map(Addresses::normalize).toSet()
        val fromKey = Addresses.normalize(from)
        val others = recipients
            .filter { it.isNotBlank() && it != MmsProviderStore.INSERT_ADDRESS_TOKEN }
            .distinctBy(Addresses::normalize)
            .filterNot { Addresses.normalize(it) == fromKey }
        val withoutSelf = others.filterNot { Addresses.normalize(it) in own }
        val group = when {
            withoutSelf.size < others.size -> withoutSelf
            others.size <= 1 -> emptyList()
            else -> others
        }
        return listOf(from) + group
    }
}
