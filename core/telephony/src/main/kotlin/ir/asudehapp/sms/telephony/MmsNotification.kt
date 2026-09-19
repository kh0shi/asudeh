package ir.asudehapp.sms.telephony

import java.nio.charset.Charset

/**
 * اعلان MMS (`M-Notification.ind`، OMA MMS Encapsulation §6.2) که با
 * `WAP_PUSH_DELIVER` می‌رسد. خود پیام روی MMSC اپراتور است و این اعلان فقط
 * می‌گوید از کجا دریافتش کنیم.
 *
 * تا وقتی MMS کامل پیاده نشده (D26)، آسوده دست‌کم همین اعلان را در provider
 * می‌نویسد و به کاربر خبر می‌دهد، تا MMS بی‌صدا گم نشود (`SilentLoss`).
 */
data class MmsNotification(
    val transactionId: String,
    val contentLocation: String,
    val from: String?,
    val subject: String?,
    val messageSize: Long?,
    /** زمان انقضا روی MMSC، بر حسب ثانیه از epoch. */
    val expirySeconds: Long?,
    val mmsVersion: Int?,
) {
    companion object {
        private const val MESSAGE_TYPE = 0x0C
        private const val TYPE_NOTIFICATION_IND = 0x82
        private const val TRANSACTION_ID = 0x18
        private const val MMS_VERSION = 0x0D
        private const val FROM = 0x09
        private const val SUBJECT = 0x16
        private const val MESSAGE_SIZE = 0x0E
        private const val EXPIRY = 0x08
        private const val CONTENT_LOCATION = 0x03

        private const val ADDRESS_PRESENT = 0x80
        private const val ABSOLUTE_TIME = 0x80

        /**
         * خروجی `null` یعنی این PDU اعلان MMS نیست یا خراب است. [nowSeconds] برای
         * تبدیل انقضای نسبی به مطلق است.
         */
        fun parse(pdu: ByteArray, nowSeconds: Long): MmsNotification? =
            runCatching { Reader(pdu).readNotification(nowSeconds) }.getOrNull()

        private class Reader(private val bytes: ByteArray) {
            private var pos = 0

            fun readNotification(nowSeconds: Long): MmsNotification? {
                var type: Int? = null
                var transactionId: String? = null
                var location: String? = null
                var from: String? = null
                var subject: String? = null
                var size: Long? = null
                var expiry: Long? = null
                var version: Int? = null

                while (pos < bytes.size) {
                    val field = next()
                    if (field < 0x80) {
                        // سرایند برنامه: نام و مقدار هر دو متن‌اند.
                        pos--
                        readText()
                        readText()
                        continue
                    }
                    when (field and 0x7F) {
                        MESSAGE_TYPE -> type = next()
                        TRANSACTION_ID -> transactionId = readText()
                        MMS_VERSION -> version = next() and 0x7F
                        CONTENT_LOCATION -> location = readText()
                        SUBJECT -> subject = readEncodedString()
                        MESSAGE_SIZE -> size = readLong()
                        FROM -> {
                            val end = readValueLength().let { pos + it }
                            if (next() == ADDRESS_PRESENT) {
                                from = readEncodedString().substringBefore("/TYPE=")
                            }
                            pos = end
                        }
                        EXPIRY -> {
                            val end = readValueLength().let { pos + it }
                            val token = next()
                            val value = readLong()
                            expiry = if (token == ABSOLUTE_TIME) value else nowSeconds + value
                            pos = end
                        }
                        else -> skipValue()
                    }
                }
                if (type != TYPE_NOTIFICATION_IND) return null
                return MmsNotification(
                    transactionId = transactionId ?: return null,
                    contentLocation = location ?: return null,
                    from = from,
                    subject = subject,
                    messageSize = size,
                    expirySeconds = expiry,
                    mmsVersion = version,
                )
            }

            private fun next(): Int {
                if (pos >= bytes.size) throw IndexOutOfBoundsException("PDU کوتاه است")
                return bytes[pos++].toInt() and 0xFF
            }

            private fun readUintVar(): Long {
                var value = 0L
                repeat(5) {
                    val b = next()
                    value = (value shl 7) or (b and 0x7F).toLong()
                    if (b and 0x80 == 0) return value
                }
                error("uintvar بیش از حد بلند است")
            }

            private fun readValueLength(): Int {
                val first = next()
                return when {
                    first <= 30 -> first
                    first == 31 -> readUintVar().toInt()
                    else -> error("طول نامعتبر")
                }
            }

            /** Long-integer: یک بایت طول (۱ تا ۳۰) و بعد عدد big-endian. */
            private fun readLong(): Long {
                val length = next()
                if (length > 30) return (length and 0x7F).toLong() // short-integer
                var value = 0L
                repeat(length) { value = (value shl 8) or next().toLong() }
                return value
            }

            private fun readText(charset: Charset = Charsets.UTF_8): String {
                if (pos < bytes.size && (bytes[pos].toInt() and 0xFF) == 0x7F) pos++
                val start = pos
                while (pos < bytes.size && bytes[pos].toInt() != 0) pos++
                val text = String(bytes, start, pos - start, charset)
                if (pos < bytes.size) pos++ // نویسهٔ پایانی 0x00
                return text.removePrefix("\"")
            }

            /** Encoded-string-value: یا متن ساده، یا طول + charset + متن. */
            private fun readEncodedString(): String {
                val first = bytes[pos].toInt() and 0xFF
                if (first > 31) return readText()
                val end = readValueLength() + pos
                val mib = if ((bytes[pos].toInt() and 0xFF) >= 0x80) next() and 0x7F else readLong().toInt()
                val text = readText(charsetOf(mib))
                pos = end
                return text
            }

            /** رد شدن از مقدار سرایندی که نمی‌شناسیم (WAP-230 §8.4.1.2). */
            private fun skipValue() {
                val first = next()
                when {
                    first <= 30 -> pos += first
                    first == 31 -> pos += readUintVar().toInt()
                    first in 32..127 -> {
                        pos--
                        readText()
                    }
                    else -> Unit // short-integer
                }
            }

            private fun charsetOf(mib: Int): Charset = when (mib) {
                3 -> Charsets.US_ASCII
                4 -> Charsets.ISO_8859_1
                1000, 1013 -> Charsets.UTF_16BE
                1015 -> Charsets.UTF_16
                else -> Charsets.UTF_8
            }
        }
    }
}
