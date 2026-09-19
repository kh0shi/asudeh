package ir.asudehapp.sms.model

/** ورودی `Classifier`. عمداً هیچ چیزی از قواعد کاربر در آن نیست (D29). */
data class MessageInput(
    val address: String,
    val body: String,
    /** فرستنده در مخاطب‌های گوشی هست. برای قفل ایمنی D31 استفاده می‌شود. */
    val isKnownContact: Boolean = false,
)

/** قواعد کاربر. — GLOSSARY: Allowlist، Blocklist */
data class UserRules(
    val allowlist: Set<String> = emptySet(),
    val blocklist: Set<String> = emptySet(),
) {
    fun isAllowed(address: String): Boolean = Addresses.normalize(address) in normalizedAllow
    fun isBlocked(address: String): Boolean = Addresses.normalize(address) in normalizedBlock

    private val normalizedAllow: Set<String> = allowlist.mapTo(mutableSetOf(), Addresses::normalize)
    private val normalizedBlock: Set<String> = blocklist.mapTo(mutableSetOf(), Addresses::normalize)

    companion object {
        val EMPTY: UserRules = UserRules()
    }
}

/** یکسان‌سازی سرشماره‌ها، تا `+989…` و `989…` و `09…` یک چیز شمرده شوند. */
object Addresses {

    private val AD_LINE_PREFIXES = listOf("1000", "2000", "3000", "5000", "9000")

    fun normalize(address: String): String {
        val digitsOnly = buildString {
            for (ch in address.trim()) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '+' && isEmpty() -> append(ch)
                    ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> Unit
                    else -> append(ch)
                }
            }
        }
        if (digitsOnly.isEmpty()) return address.trim()
        if (digitsOnly.any { !it.isDigit() && it != '+' }) return digitsOnly.lowercase()
        val bare = digitsOnly.removePrefix("+")
        return when {
            bare.startsWith("0098") -> "0" + bare.removePrefix("0098")
            bare.startsWith("98") && bare.length >= 12 -> "0" + bare.removePrefix("98")
            else -> bare
        }
    }

    fun kindOf(address: String): SenderKind {
        val normalized = normalize(address)
        if (normalized.isEmpty()) return SenderKind.ALPHANUMERIC
        if (normalized.any { !it.isDigit() && it != '+' }) return SenderKind.ALPHANUMERIC
        val digits = normalized.removePrefix("+")
        return when {
            digits.startsWith("09") && digits.length == 11 -> SenderKind.MOBILE
            AD_LINE_PREFIXES.any { digits.startsWith(it) } -> SenderKind.AD_LINE
            digits.length in 3..9 -> SenderKind.SHORT_CODE
            else -> SenderKind.OTHER_NUMBER
        }
    }

    /** خطوط انبوه ایرانی؛ فقط برای این‌ها `Unsub11` پیشنهاد می‌شود (D28). */
    fun isAdLine(address: String): Boolean = kindOf(address) == SenderKind.AD_LINE
}
