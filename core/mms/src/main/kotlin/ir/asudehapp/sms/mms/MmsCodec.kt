package ir.asudehapp.sms.mms

import ir.asudehapp.sms.mms.pdu.CharacterSets
import ir.asudehapp.sms.mms.pdu.ContentType
import ir.asudehapp.sms.mms.pdu.EncodedStringValue
import ir.asudehapp.sms.mms.pdu.MultimediaMessagePdu
import ir.asudehapp.sms.mms.pdu.NotifyRespInd
import ir.asudehapp.sms.mms.pdu.PduBody
import ir.asudehapp.sms.mms.pdu.PduComposer
import ir.asudehapp.sms.mms.pdu.PduHeaders
import ir.asudehapp.sms.mms.pdu.PduParser
import ir.asudehapp.sms.mms.pdu.PduPart
import ir.asudehapp.sms.mms.pdu.RetrieveConf
import ir.asudehapp.sms.mms.pdu.SendConf
import ir.asudehapp.sms.mms.pdu.SendReq
import java.nio.charset.Charset

/** یک part از پیام چندرسانه‌ای: متن، تصویر، صدا، ویدیو یا SMIL. */
class MmsPart(
    val contentType: String,
    val data: ByteArray,
    /** نام فایل یا `Content-Location`. */
    val name: String? = null,
    val contentId: String? = null,
    /** MIBenum نویسه‌گذاری متن (۱۰۶ یعنی UTF-8). */
    val charset: Int = CharacterSets.UTF_8,
) {
    val isText: Boolean get() = contentType.equals(ContentType.TEXT_PLAIN, ignoreCase = true)
    val isSmil: Boolean get() = contentType.equals(ContentType.APP_SMIL, ignoreCase = true)
    val isImage: Boolean get() = contentType.startsWith("image/", ignoreCase = true)

    /** پیوست واقعی: هر چیزی جز متن و SMIL. */
    val isAttachment: Boolean get() = !isText && !isSmil

    /** متن این part با نویسه‌گذاری خودش؛ برای part غیرمتنی `null`. */
    fun text(): String? {
        if (!isText) return null
        val charset = runCatching { Charset.forName(CharacterSets.getMimeName(charset)) }
            .getOrDefault(Charsets.UTF_8)
        return String(data, charset)
    }
}

/** پیام چندرسانه‌ای دریافت‌شده (`M-Retrieve.conf`). */
class IncomingMms(
    val from: String?,
    val to: List<String>,
    val cc: List<String>,
    val subject: String?,
    /** زمان ارسال بر حسب ثانیه؛ ۰ یعنی نامعلوم. */
    val dateSeconds: Long,
    val messageId: String?,
    val transactionId: String?,
    val contentType: String?,
    val messageClass: String?,
    val mmsVersion: Int,
    val parts: List<MmsPart>,
) {
    /** متن‌های پیام، کنار هم. */
    val text: String
        get() = parts.mapNotNull { it.text() }.filter { it.isNotBlank() }.joinToString("\n")

    val attachments: List<MmsPart> get() = parts.filter { it.isAttachment }
}

/** پاسخ MMSC به ارسال (`M-Send.conf`). */
class SendResult(val responseStatus: Int, val messageId: String?) {
    val ok: Boolean get() = responseStatus == PduHeaders.RESPONSE_STATUS_OK
}

/**
 * خواندن و ساختن PDUهای MMS (OMA MMS Encapsulation). کار واقعی را کد AOSP
 * انجام می‌دهد؛ این لایه فقط آن را به نوع‌های ساده‌ی Kotlin تبدیل می‌کند.
 */
object MmsCodec {

    /** خروجی `null` یعنی PDU خراب است یا `M-Retrieve.conf` نیست. */
    fun parseRetrieveConf(pdu: ByteArray): IncomingMms? {
        val parsed = runCatching { PduParser(pdu, true).parse() }.getOrNull() as? RetrieveConf ?: return null
        return IncomingMms(
            from = parsed.from?.string?.let(::cleanAddress),
            to = parsed.to.addresses(),
            cc = parsed.cc.addresses(),
            subject = parsed.subject?.string?.takeIf { it.isNotBlank() },
            dateSeconds = parsed.date,
            messageId = parsed.messageId?.let { String(it) },
            transactionId = parsed.transactionId?.let { String(it) },
            contentType = parsed.contentType?.let { String(it) },
            messageClass = parsed.messageClass?.let { String(it) },
            mmsVersion = parsed.mmsVersion,
            parts = parsed.body.toParts(),
        )
    }

    fun parseSendConf(pdu: ByteArray): SendResult? {
        val parsed = runCatching { PduParser(pdu).parse() }.getOrNull() as? SendConf ?: return null
        return SendResult(parsed.responseStatus, parsed.messageId?.let { String(it) })
    }

    /**
     * ساختن `M-Send.req`. برای گروه، همهٔ گیرنده‌ها در `To` می‌آیند تا پاسخ‌ها
     * به همه برسد. یک SMIL ساده هم ساخته می‌شود، چون بعضی گوشی‌ها بدون آن
     * پیوست را نشان نمی‌دهند.
     */
    fun composeSendReq(
        recipients: List<String>,
        text: String?,
        attachments: List<MmsPart>,
        subject: String? = null,
        deliveryReport: Boolean = false,
    ): ByteArray {
        require(recipients.isNotEmpty()) { "گیرنده‌ای نیست" }
        val request = SendReq()
        request.to = recipients.map { EncodedStringValue(it) }.toTypedArray()
        subject?.takeIf { it.isNotBlank() }?.let { request.subject = EncodedStringValue(it) }
        request.date = System.currentTimeMillis() / 1_000
        request.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        request.expiry = WEEK_SECONDS
        request.priority = PduHeaders.PRIORITY_NORMAL
        request.deliveryReport = if (deliveryReport) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO
        request.readReport = PduHeaders.VALUE_NO

        val parts = buildList {
            text?.takeIf { it.isNotEmpty() }?.let {
                add(MmsPart(ContentType.TEXT_PLAIN, it.toByteArray(Charsets.UTF_8), name = "text.txt"))
            }
            attachments.forEachIndexed { index, part ->
                add(part.withName(part.name ?: "part$index.${extensionOf(part.contentType)}"))
            }
        }
        val body = PduBody()
        body.addPart(smil(parts).toPduPart())
        parts.forEach { body.addPart(it.toPduPart()) }
        request.body = body
        request.messageSize = parts.sumOf { it.data.size }.toLong()

        return PduComposer(request).make() ?: error("ساختن PDU ممکن نشد")
    }

    /**
     * `M-NotifyResp.ind`: به MMSC می‌گوید پیام دریافت شد، تا دوباره فرستاده
     * نشود.
     */
    fun composeNotifyResp(transactionId: String, mmsVersion: Int): ByteArray {
        val response = NotifyRespInd(
            mmsVersion.takeIf { it != 0 } ?: PduHeaders.CURRENT_MMS_VERSION,
            transactionId.toByteArray(),
            PduHeaders.STATUS_RETRIEVED,
        )
        response.reportAllowed = PduHeaders.VALUE_NO
        return PduComposer(response).make() ?: error("ساختن PDU ممکن نشد")
    }

    private fun smil(parts: List<MmsPart>): MmsPart {
        val regions = buildString {
            if (parts.any { it.isImage || it.contentType.startsWith("video/") }) {
                append("""<region id="Image" width="100%" height="80%" left="0%" top="0%" fit="meet"/>""")
            }
            append("""<region id="Text" width="100%" height="20%" left="0%" top="80%" fit="scroll"/>""")
        }
        val pars = parts.joinToString("") { part ->
            val src = part.name
            val element = when {
                part.isText -> """<text src="$src" region="Text"/>"""
                part.isImage -> """<img src="$src" region="Image"/>"""
                part.contentType.startsWith("video/") -> """<video src="$src" region="Image"/>"""
                part.contentType.startsWith("audio/") -> """<audio src="$src"/>"""
                else -> """<ref src="$src"/>"""
            }
            """<par dur="5000ms">$element</par>"""
        }
        val smil = "<smil><head><layout><root-layout/>$regions</layout></head><body>$pars</body></smil>"
        return MmsPart(ContentType.APP_SMIL, smil.toByteArray(Charsets.UTF_8), name = "smil.xml", contentId = "<smil>")
    }

    private fun MmsPart.withName(name: String) = MmsPart(contentType, data, name, contentId, charset)

    private fun MmsPart.toPduPart(): PduPart = PduPart().also { part ->
        part.contentType = contentType.toByteArray()
        val location = name ?: "part"
        part.contentLocation = location.toByteArray()
        part.name = location.toByteArray()
        part.contentId = (contentId ?: "<${location.substringBeforeLast('.')}>").toByteArray()
        if (isText || isSmil) part.charset = CharacterSets.UTF_8
        part.data = data
    }

    private fun PduBody?.toParts(): List<MmsPart> {
        this ?: return emptyList()
        return (0 until partsNum).mapNotNull { index ->
            val part = getPart(index) ?: return@mapNotNull null
            val type = part.contentType?.let { String(it) }?.lowercase() ?: return@mapNotNull null
            MmsPart(
                contentType = type,
                data = part.data ?: ByteArray(0),
                name = (part.contentLocation ?: part.name ?: part.filename)?.let { String(it) },
                contentId = part.contentId?.let { String(it) },
                charset = part.charset.takeIf { it != 0 } ?: CharacterSets.UTF_8,
            )
        }
    }

    private fun Array<EncodedStringValue>?.addresses(): List<String> =
        this.orEmpty().mapNotNull { it.string?.let(::cleanAddress) }.filter { it.isNotEmpty() }

    /** «0912…/TYPE=PLMN» → «0912…». */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    private fun extensionOf(contentType: String): String = when (contentType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/3gpp" -> "3gp"
        "audio/amr" -> "amr"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/aac" -> "m4a"
        "text/x-vcard", "text/vcard" -> "vcf"
        else -> "bin"
    }

    private const val WEEK_SECONDS = 7 * 24 * 60 * 60L
}
