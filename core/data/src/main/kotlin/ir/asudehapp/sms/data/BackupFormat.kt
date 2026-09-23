package ir.asudehapp.sms.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer
import java.util.Base64

/**
 * قالب فایل پشتیبان (D57): JSON Lines. خط اول سرایند است (نسخه، تنظیمات و
 * قواعد کاربر) و هر خط بعدی یک پیامک یا MMS. این‌طور ۱۰۰ هزار پیامک بدون بار
 * کردن همه در حافظه نوشته و خوانده می‌شود.
 *
 * از نسخهٔ ۲، MMS هم جزو پشتیبان است (ADR-0009 دنباله): partهای هر MMS
 * (عکس، صدا، ...) با base64 همان‌جا توی خط JSON خودش جا می‌شوند، تا فایل
 * تک‌تکه بماند و zip/پوشهٔ جدا لازم نشود؛ هزینه‌اش حدود ۳۳ درصد حجم بیشتر است.
 *
 * فایل روی گوشی کاربر و هر جا که خودش انتخاب کند می‌ماند؛ اپ آن را به هیچ
 * جایی نمی‌فرستد (`NoNet`).
 */
object BackupFormat {

    const val FORMAT: String = "asudeh-backup"
    const val VERSION: Int = 2
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

    /** یک پیامک یا MMS در پشتیبان؛ برای خواندن یکجای هر دو نوع پشت‌سرهم. */
    sealed interface Message {
        /** کلید تکراری بودن: همان پیام دو بار بازگردانی نمی‌شود. */
        val key: String
    }

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
        /**
         * تشخیص نوع خط. پشتیبان‌های نسخهٔ ۱ این کلید را ندارند و همه‌شان پیامک
         * عادی‌اند، برای همین پیش‌فرض «sms» است.
         */
        val kind: String = "sms",
    ) : Message {
        override val key: String get() = dedupeKey(address, date, type, body)
    }

    /** یک part از MMS (عکس، صدا، متن، ...)، هم‌شکل با `ir.asudehapp.sms.mms.MmsPart`. */
    @Serializable
    data class Part(
        val contentType: String,
        /** دادهٔ part با base64. */
        val data: String,
        val name: String? = null,
        val contentId: String? = null,
        /** MIBenum نویسه‌گذاری متن (۱۰۶ یعنی UTF-8). */
        val charset: Int = 106,
    )

    @Serializable
    data class Mms(
        val address: String,
        /** طرف‌های دیگر گفتگوی گروهی، جدا با «|» (همان قرارداد `MessageEntity.recipients`). */
        val recipients: String = "",
        val date: Long,
        val dateSent: Long = 0,
        val outgoing: Boolean,
        val read: Boolean = true,
        val subId: Int = -1,
        val folder: String? = null,
        val parts: List<Part> = emptyList(),
        val kind: String = "mms",
    ) : Message {
        /**
         * کلید تکراری بودن. متن یا partها معیار مطمئنی نیستند (عکس یک پیام ممکن
         * است فشرده یا کمی متفاوت بازتولید شود)، پس مثل پیامک از نشانی و زمان
         * استفاده می‌کنیم؛ طرف‌های گروه هم اضافه می‌شود تا دو گفتگوی گروهی
         * هم‌زمان با هم قاطی نشوند.
         */
        override val key: String get() = mmsDedupeKey(address, recipients, date, outgoing)
    }

    fun dedupeKey(address: String, date: Long, type: Int, body: String): String =
        "${ir.asudehapp.sms.model.Addresses.normalize(address)}|$date|$type|${body.hashCode()}"

    fun mmsDedupeKey(address: String, recipients: String, date: Long, outgoing: Boolean): String =
        "${ir.asudehapp.sms.model.Addresses.normalize(address)}|$recipients|$date|$outgoing"

    /** رمزگذاری base64 دادهٔ یک part، برای نوشتن توی خط JSON. */
    fun encodePartData(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    /** رمزگشایی base64 دادهٔ یک part، هنگام بازگردانی. */
    fun decodePartData(data: String): ByteArray = Base64.getDecoder().decode(data)

    fun writeHeader(writer: Writer, header: Header) {
        writer.write(json.encodeToString(Header.serializer(), header))
        writer.write("\n")
    }

    fun writeSms(writer: Writer, sms: Sms) {
        writer.write(json.encodeToString(Sms.serializer(), sms))
        writer.write("\n")
    }

    fun writeMms(writer: Writer, mms: Mms) {
        writer.write(json.encodeToString(Mms.serializer(), mms))
        writer.write("\n")
    }

    /**
     * خواندن سرایند. فایلی که پشتیبان آسوده نیست یا از نسخهٔ بعدی است پذیرفته
     * نمی‌شود، به‌جای اینکه نیمه‌کاره خوانده شود. پشتیبان نسخهٔ ۱ (بدون MMS)
     * همچنان قابل خواندن است.
     */
    fun readHeader(reader: BufferedReader): Header {
        val line = reader.readLine() ?: throw BackupException(BackupException.Reason.EMPTY)
        val header = runCatching { json.decodeFromString(Header.serializer(), line) }
            .getOrElse { throw BackupException(BackupException.Reason.NOT_A_BACKUP) }
        if (header.format != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (header.version > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return header
    }

    /** فقط برای تشخیص نوع خط، پیش از رمزگشایی کامل آن به `Sms` یا `Mms`. */
    @Serializable
    private data class Envelope(val kind: String = "sms")

    /**
     * پیام‌ها (پیامک و MMS)، یکی‌یکی و به همان ترتیب فایل. خط خراب نادیده گرفته
     * می‌شود و شمرده می‌شود. چون فایل یک‌بار و پشت‌سرهم خوانده می‌شود (D57)، این
     * تنها راه درست برای خواندن پیامک و MMS با هم از یک `reader` است؛ [readSms]
     * و [readMms] رویش نوشته شده‌اند.
     */
    fun readMessages(reader: BufferedReader, onCorrupt: () -> Unit = {}): Sequence<Message> =
        generateSequence { reader.readLine() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val kind = runCatching { json.decodeFromString(Envelope.serializer(), line) }.getOrNull()?.kind
                if (kind == null) {
                    onCorrupt()
                    return@mapNotNull null
                }
                val message = if (kind == "mms") {
                    runCatching { json.decodeFromString(Mms.serializer(), line) }
                } else {
                    runCatching { json.decodeFromString(Sms.serializer(), line) }
                }
                message.onFailure { onCorrupt() }.getOrNull()
            }

    /** فقط پیامک‌ها؛ خط‌های MMS رد می‌شوند (بدون شمرده شدن به‌عنوان خراب). */
    fun readSms(reader: BufferedReader, onCorrupt: () -> Unit = {}): Sequence<Sms> =
        readMessages(reader, onCorrupt).filterIsInstance<Sms>()

    /** فقط MMSها؛ خط‌های پیامک رد می‌شوند (بدون شمرده شدن به‌عنوان خراب). */
    fun readMms(reader: BufferedReader, onCorrupt: () -> Unit = {}): Sequence<Mms> =
        readMessages(reader, onCorrupt).filterIsInstance<Mms>()
}

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { EMPTY, NOT_A_BACKUP, NEWER_VERSION, NOT_DEFAULT_APP }
}
