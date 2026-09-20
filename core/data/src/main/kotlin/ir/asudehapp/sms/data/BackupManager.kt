package ir.asudehapp.sms.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.persian.KeywordMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

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
) {

    suspend fun export(output: OutputStream, appVersion: String, settings: AsudehSettings): ExportResult =
        withContext(Dispatchers.IO) {
            val rules = rulesDatabase.senderRules().all()
            val keywords = rulesDatabase.keywordRules().all()
            val folders = repository.foldersByProviderId(MessageEntity.KIND_SMS)
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
                                folder = folders[cursor.getLong(id)]?.name,
                            ),
                        )
                        count++
                    }
                } ?: error("Telephony Provider در دسترس نیست")
            }
            ExportResult(count, rules.size + keywords.size)
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

                val existing = existingKeys()
                var added = 0
                var duplicates = 0
                var corrupt = 0
                val placements = HashMap<Long, Folder>()
                for (sms in BackupFormat.readSms(reader) { corrupt++ }) {
                    if (!existing.add(sms.key)) {
                        duplicates++
                        continue
                    }
                    val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, sms.toValues())
                    if (uri == null) {
                        corrupt++
                        continue
                    }
                    added++
                    val folder = sms.folder?.let { runCatching { Folder.valueOf(it) }.getOrNull() }
                    if (folder != null && folder != Folder.INBOX) placements[ContentUris.parseId(uri)] = folder
                }

                // پیامک‌های تازه با همگام‌سازی وارد ایندکس می‌شوند، و جایی که کاربر
                // پیش‌تر برایشان انتخاب کرده بود برمی‌گردد.
                repository.sync()
                repository.restoreFolders(MessageEntity.KIND_SMS, placements)
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
    }
}
