package ir.asudehapp.sms.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer

/**
 * قالب فایل پشتیبان (D57): JSON Lines. خط اول سرایند است (نسخه، تنظیمات و
 * قواعد کاربر) و هر خط بعدی یک پیامک. این‌طور ۱۰۰ هزار پیامک بدون بار کردن
 * همه در حافظه نوشته و خوانده می‌شود.
 *
 * فایل روی گوشی کاربر و هر جا که خودش انتخاب کند می‌ماند؛ اپ آن را به هیچ
 * جایی نمی‌فرستد (`NoNet`). MMS در این نسخه جزو پشتیبان نیست.
 */
object BackupFormat {

    const val FORMAT: String = "asudeh-backup"
    const val VERSION: Int = 1
    const val MIME_TYPE: String = "application/octet-stream"
    const val EXTENSION: String = "asudeh"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Header(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val createdAt: Long,
        val appVersion: String,
        val settings: Map<String, String> = emptyMap(),
        val rules: List<Rule> = emptyList(),
        /** کلیدواژه‌های «قواعد من» (ADR-0012). پشتیبان‌های قدیمی‌تر این را ندارند. */
        val keywordRules: List<KeywordRule> = emptyList(),
    )

    @Serializable
    data class Rule(val address: String, val kind: String, val createdAt: Long)

    @Serializable
    data class KeywordRule(val keyword: String, val kind: String, val createdAt: Long)

    @Serializable
    data class Sms(
        val address: String,
        val body: String,
        val date: Long,
        val dateSent: Long = 0,
        /** `Telephony.Sms.TYPE`: ۱ دریافتی، ۲ ارسالی، ... */
        val type: Int,
        val read: Boolean = true,
        val subId: Int = -1,
        /** جای پیامک در آسوده، تا `Rescue`ها و جابه‌جایی‌های کاربر از دست نروند. */
        val folder: String? = null,
    ) {
        /** کلید تکراری بودن: همان پیامک دو بار بازگردانی نمی‌شود. */
        val key: String get() = dedupeKey(address, date, type, body)
    }

    fun dedupeKey(address: String, date: Long, type: Int, body: String): String =
        "${ir.asudehapp.sms.model.Addresses.normalize(address)}|$date|$type|${body.hashCode()}"

    fun writeHeader(writer: Writer, header: Header) {
        writer.write(json.encodeToString(Header.serializer(), header))
        writer.write("\n")
    }

    fun writeSms(writer: Writer, sms: Sms) {
        writer.write(json.encodeToString(Sms.serializer(), sms))
        writer.write("\n")
    }

    /**
     * خواندن سرایند. فایلی که پشتیبان آسوده نیست یا از نسخهٔ بعدی است پذیرفته
     * نمی‌شود، به‌جای اینکه نیمه‌کاره خوانده شود.
     */
    fun readHeader(reader: BufferedReader): Header {
        val line = reader.readLine() ?: throw BackupException(BackupException.Reason.EMPTY)
        val header = runCatching { json.decodeFromString(Header.serializer(), line) }
            .getOrElse { throw BackupException(BackupException.Reason.NOT_A_BACKUP) }
        if (header.format != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (header.version > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return header
    }

    /** پیامک‌ها، یکی‌یکی. خط خراب نادیده گرفته می‌شود و شمرده می‌شود. */
    fun readSms(reader: BufferedReader, onCorrupt: () -> Unit = {}): Sequence<Sms> =
        generateSequence { reader.readLine() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching { json.decodeFromString(Sms.serializer(), line) }
                    .onFailure { onCorrupt() }
                    .getOrNull()
            }
}

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { EMPTY, NOT_A_BACKUP, NEWER_VERSION, NOT_DEFAULT_APP }
}
