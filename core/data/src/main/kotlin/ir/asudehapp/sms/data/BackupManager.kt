package ir.asudehapp.sms.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import ir.asudehapp.sms.mms.MmsPart
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.persian.KeywordMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

/** نتیجهٔ ساختن پشتیبان. */
data class ExportResult(val messages: Int, val rules: Int)

/** نتیجهٔ بازگردانی پشتیبان. */
data class ImportResult(
    val added: Int,
    /** پیامک‌هایی که از قبل در گوشی بودند و دوباره نوشته نشدند. */
    val duplicates: Int,
    /** خط‌های خراب فایل. */
    val corrupt: Int,
    val rules: Int,
)

/**
 * پشتیبان دستی به فایل با SAF (D57): پیامک‌ها، «قواعد من» و تنظیمات. پشتیبان
 * ابری عمداً خاموش است؛ این فایل فقط جایی می‌رود که خود کاربر انتخاب کند.
 */
class BackupManager(
    private val context: Context,
    private val repository: AsudehRepository,
    private val rulesDatabase: RulesDatabase = RulesDatabase.get(context),
    private val mmsStore: MmsProviderStore = MmsProviderStore(context),
) {

    /**
     * خواندن MMS همیشه در دسترس است (مثل همگام‌سازی D26)، برخلاف نوشتنش که
     * فقط برای اپ پیش‌فرض ممکن است؛ پس ساختن پشتیبان این محدودیت را ندارد.
     */
    suspend fun export(output: OutputStream, appVersion: String, settings: AsudehSettings): ExportResult =
        withContext(Dispatchers.IO) {
            val rules = rulesDatabase.senderRules().all()
            val keywords = rulesDatabase.keywordRules().all()
            val smsFolders = repository.foldersByProviderId(MessageEntity.KIND_SMS)
            val mmsFolders = repository.foldersByProviderId(MessageEntity.KIND_MMS)
            var count = 0
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                BackupFormat.writeHeader(
                    writer,
                    BackupFormat.Header(
                        createdAt = System.currentTimeMillis(),
                        appVersion = appVersion,
                        settings = settings.export(),
                        rules = rules.map { BackupFormat.Rule(it.address, it.kind.name, it.createdAt) },
                        keywordRules = keywords.map {
                            BackupFormat.KeywordRule(it.keyword, it.kind.name, it.createdAt)
                        },
                    ),
                )
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    PROJECTION,
                    "${Telephony.Sms.TYPE} != ?",
                    arrayOf(Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val dateSent = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
                    val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                    val read = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                    val subId = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
                    while (cursor.moveToNext()) {
                        BackupFormat.writeSms(
                            writer,
                            BackupFormat.Sms(
                                address = cursor.getString(address).orEmpty(),
                                body = cursor.getString(body).orEmpty(),
                                date = cursor.getLong(date),
                                dateSent = cursor.getLong(dateSent),
                                type = cursor.getInt(type),
                                read = cursor.getInt(read) == 1,
                                subId = cursor.getInt(subId),
                                folder = smsFolders[cursor.getLong(id)]?.name,
                            ),
                        )
                        count++
                    }
                } ?: error("Telephony Provider در دسترس نیست")
                count += exportMms(writer, mmsFolders)
            }
            ExportResult(count, rules.size + keywords.size)
        }

    /**
     * MMSها، partهاشان با [MmsProviderStore.allParts] و base64. اعلان‌هایی که
     * فقط رسیده‌اند و خود پیام هنوز دریافت نشده (`pendingDownload`) رد
     * می‌شوند: چیزی برای پشتیبان‌گیری ندارند و با دریافت دوباره یا اعلان WAP
     * بعدی جایگزین می‌شوند.
     */
    private suspend fun exportMms(writer: Writer, folders: Map<Long, Folder>): Int {
        val threadRecipients = mmsStore.threadRecipients()
        var count = 0
        for (chunk in mmsStore.readIds().chunked(MMS_CHUNK)) {
            for (pm in mmsStore.readByIds(chunk, threadRecipients)) {
                if (pm.pendingDownload) continue
                val parts = mmsStore.allParts(pm.id)
                BackupFormat.writeMms(
                    writer,
                    BackupFormat.Mms(
                        address = pm.address,
                        recipients = pm.recipients.toBackupRecipients(),
                        date = pm.date,
                        dateSent = pm.dateSent,
                        outgoing = pm.outgoing,
                        read = pm.read,
                        subId = pm.subId,
                        folder = folders[pm.id]?.name,
                        parts = parts.map {
                            BackupFormat.Part(
                                contentType = it.contentType,
                                data = BackupFormat.encodePartData(it.data),
                                name = it.name,
                                contentId = it.contentId,
                                charset = it.charset,
                            )
                        },
                    ),
                )
                count++
            }
        }
        return count
    }

    /**
     * بازگردانی. پیامکی که از قبل در گوشی هست دوباره نوشته نمی‌شود. قواعد کاربر
     * ادغام می‌شوند: قاعده‌ای که کاربر روی همین گوشی گذاشته، جایش را به فایل
     * نمی‌دهد. نوشتن پیامک فقط برای اپ پیش‌فرض ممکن است.
     */
    suspend fun import(input: InputStream, settings: AsudehSettings, isDefaultApp: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val header = BackupFormat.readHeader(reader)
                if (!isDefaultApp) throw BackupException(BackupException.Reason.NOT_DEFAULT_APP)

                settings.import(header.settings)
                val ruleDao = rulesDatabase.senderRules()
                val existingRules = ruleDao.all().map { it.address }.toSet()
                var rules = 0
                for (rule in header.rules) {
                    if (rule.address in existingRules) continue
                    val kind = runCatching { SenderRuleKind.valueOf(rule.kind) }.getOrNull() ?: continue
                    ruleDao.put(SenderRuleEntity(rule.address, kind, rule.createdAt))
                    rules++
                }

                val keywordDao = rulesDatabase.keywordRules()
                val existingKeywords = keywordDao.all().map { it.normalized }.toSet()
                for (rule in header.keywordRules) {
                    val normalized = KeywordMatch.key(rule.keyword)
                    if (normalized.isEmpty() || normalized in existingKeywords) continue
                    val kind = runCatching { SenderRuleKind.valueOf(rule.kind) }.getOrNull() ?: continue
                    keywordDao.put(KeywordRuleEntity(normalized, rule.keyword, kind, rule.createdAt))
                    rules++
                }

                // پیامک و MMS پشت‌سرهم و یکجا خوانده می‌شوند (فایل یک‌بار و
                // پشت‌سرهم است، D57)، پس هر دو نوع تکراری‌یابی و نوشتن‌شان اینجا
                // با هم پیش می‌رود.
                val existingSms = existingKeys()
                val existingMms = existingMmsKeys()
                var added = 0
                var duplicates = 0
                var corrupt = 0
                val smsPlacements = HashMap<Long, Folder>()
                val mmsPlacements = HashMap<Long, Folder>()
                for (message in BackupFormat.readMessages(reader) { corrupt++ }) {
                    when (message) {
                        is BackupFormat.Sms -> {
                            if (!existingSms.add(message.key)) {
                                duplicates++
                                continue
                            }
                            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, message.toValues())
                            if (uri == null) {
                                corrupt++
                                continue
                            }
                            added++
                            val folder = message.folder?.let { runCatching { Folder.valueOf(it) }.getOrNull() }
                            if (folder != null && folder != Folder.INBOX) {
                                smsPlacements[ContentUris.parseId(uri)] = folder
                            }
                        }
                        is BackupFormat.Mms -> {
                            if (!existingMms.add(message.key)) {
                                duplicates++
                                continue
                            }
                            val stored = runCatching {
                                mmsStore.restore(
                                    address = message.address,
                                    recipients = message.recipients.toParticipantList(),
                                    date = message.date,
                                    dateSent = message.dateSent,
                                    outgoing = message.outgoing,
                                    read = message.read,
                                    subId = message.subId,
                                    parts = message.parts.map {
                                        MmsPart(
                                            it.contentType,
                                            BackupFormat.decodePartData(it.data),
                                            name = it.name,
                                            contentId = it.contentId,
                                            charset = it.charset,
                                        )
                                    },
                                )
                            }.getOrNull()
                            if (stored == null) {
                                corrupt++
                                continue
                            }
                            added++
                            val folder = message.folder?.let { runCatching { Folder.valueOf(it) }.getOrNull() }
                            if (folder != null && folder != Folder.INBOX) mmsPlacements[stored.providerId] = folder
                        }
                    }
                }

                // پیام‌های تازه با همگام‌سازی وارد ایندکس می‌شوند، و جایی که کاربر
                // پیش‌تر برایشان انتخاب کرده بود برمی‌گردد.
                repository.sync()
                repository.restoreFolders(MessageEntity.KIND_SMS, smsPlacements)
                repository.restoreFolders(MessageEntity.KIND_MMS, mmsPlacements)
                ImportResult(added, duplicates, corrupt, rules)
            }
        }

    private fun existingKeys(): HashSet<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                keys += BackupFormat.dedupeKey(
                    cursor.getString(0).orEmpty(),
                    cursor.getLong(1),
                    cursor.getInt(2),
                    cursor.getString(3).orEmpty(),
                )
            }
        }
        return keys
    }

    /** کلیدهای MMSهای موجود روی گوشی، هم‌شکل با [BackupFormat.Mms.key]. */
    private suspend fun existingMmsKeys(): HashSet<String> {
        val keys = HashSet<String>()
        val threadRecipients = mmsStore.threadRecipients()
        for (chunk in mmsStore.readIds().chunked(MMS_CHUNK)) {
            for (pm in mmsStore.readByIds(chunk, threadRecipients)) {
                keys += BackupFormat.mmsDedupeKey(pm.address, pm.recipients.toBackupRecipients(), pm.date, pm.outgoing)
            }
        }
        return keys
    }

    private fun BackupFormat.Sms.toValues() = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, date)
        put(Telephony.Sms.DATE_SENT, dateSent)
        put(Telephony.Sms.TYPE, type)
        put(Telephony.Sms.READ, if (read) 1 else 0)
        put(Telephony.Sms.SEEN, 1)
        if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
    }

    /** طرف‌های گروه به شکل ذخیره در پشتیبان: هم‌قرارداد با `MessageEntity.recipients`. */
    private fun List<String>.toBackupRecipients(): String =
        if (size > 1) joinToString(MessageEntity.RECIPIENT_SEPARATOR) else ""

    /** برعکس [toBackupRecipients]: رشتهٔ پشتیبان به فهرست طرف‌های گروه. */
    private fun String.toParticipantList(): List<String> =
        if (isEmpty()) emptyList() else split(MessageEntity.RECIPIENT_SEPARATOR)

    private companion object {
        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID,
        )
        const val MMS_CHUNK = 200
    }
}
