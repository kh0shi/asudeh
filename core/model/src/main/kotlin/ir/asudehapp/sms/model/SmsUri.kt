package ir.asudehapp.sms.model

/**
 * نشانی‌های `sms:`، `smsto:`، `mms:` و `mmsto:` که از شماره‌گیر، مخاطب‌ها یا
 * «پاسخ با پیامک» به اپ پیش‌فرض می‌رسند: `smsto:0912…,0935…?body=سلام`.
 */
data class SmsUri(
    val recipients: List<String>,
    val body: String?,
) {
    companion object {
        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

        fun parse(uri: String): SmsUri? {
            val scheme = uri.substringBefore(':', "").lowercase()
            if (scheme !in SCHEMES) return null
            val rest = uri.substringAfter(':').removePrefix("//")
            val recipientPart = rest.substringBefore('?')
            val query = rest.substringAfter('?', "")
            val recipients = decode(recipientPart)
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val body = query.split('&')
                .firstOrNull { it.substringBefore('=').lowercase() == "body" }
                ?.substringAfter('=', "")
                ?.let(::decode)
            return SmsUri(recipients, body)
        }

        /**
         * رمزگشایی `%XX`. برخلاف `URLDecoder`، «+» به فاصله تبدیل نمی‌شود، چون در
         * شمارهٔ بین‌المللی (`+98…`) معنا دارد.
         */
        private fun decode(text: String): String {
            if ('%' !in text) return text
            val bytes = java.io.ByteArrayOutputStream(text.length)
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '%' && i + 2 <= text.lastIndex) {
                    val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes.write(hex)
                        i += 3
                        continue
                    }
                }
                bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
                i++
            }
            return bytes.toString(Charsets.UTF_8.name())
        }
    }
}
