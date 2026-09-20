package ir.asudehapp.sms.model

/**
 * شاهد ساختاری قطعی برای `Phish` (اصل ۴). امتیاز احتمالی شاهد نیست.
 * — GLOSSARY: Evidence
 */
sealed interface Evidence {

    /** پیامک خود را از نهاد X معرفی کرده، ولی سرشماره جزو سرشماره‌های رسمی X نیست. */
    data class SenderSpoof(
        val brand: String,
        val seenSender: String,
    ) : Evidence

    /** لینک، دامنهٔ رسمی نهاد X را جعل کرده است. */
    data class DomainSpoof(
        val brand: String,
        val seenDomain: String,
        val officialDomain: String,
    ) : Evidence
}

/** کد دلیل. متن فارسی آن در لایهٔ رابط ساخته می‌شود تا `:core:classifier` به منابع اندروید وابسته نشود. */
enum class ReasonCode {
    AD_LINE_AND_PROMO_WORDS,
    PROMO_WORDS,
    BLOCKED_BY_USER,
    ALLOWED_BY_USER,

    /** کلیدواژهٔ فهرست سفید کاربر یا پیش‌فرض (ADR-0012). */
    ALLOWED_KEYWORD,

    /** کلیدواژهٔ فهرست سیاه کاربر یا پیش‌فرض (ADR-0012). */
    BLOCKED_KEYWORD,
    OTP_PATTERN,
    BANK_PATTERN,
    SERVICE_PATTERN,
    PERSONAL_NUMBER,
    KNOWN_CONTACT,
    SENDER_SPOOF,
    DOMAIN_SPOOF,
    SUSPICIOUS_LINK,
    NOT_SURE,
    CLASSIFIER_FAILED,
}

/** دلیل قابل نمایش برای جای گرفتن یک پیامک. — GLOSSARY: Reason («چرا اینجاست؟») */
data class Reason(
    val code: ReasonCode,
    val args: List<String> = emptyList(),
)

/** یک لینک پیداشده در متن پیامک (D37). */
data class DetectedLink(
    val raw: String,
    val host: String,
    /** میزبان بدون `www.` و بدون punycode، برای نمایش به کاربر. */
    val displayHost: String,
    val isPunycode: Boolean,
    val hasConfusableChars: Boolean,
)

/**
 * خروجی کامل `Classifier`: فقط «این پیامک چیست». هیچ اطلاعی از قواعد کاربر ندارد (D29).
 * — GLOSSARY: Verdict
 */
data class Verdict(
    val category: Category,
    val confidence: Confidence,
    val reason: Reason,
    val evidence: Evidence? = null,
    /** نشانهٔ مشکوک بدون `Evidence`. — GLOSSARY: Suspect */
    val risk: Boolean = false,
    val links: List<DetectedLink> = emptyList(),
) {
    companion object {
        /**
         * نتیجه‌ای که وقتی طبقه‌بند خطا می‌دهد یا از سقف زمانی می‌گذرد استفاده می‌شود.
         * طبق ADR-0003 بند ۴ پیامک در `Inbox` می‌ماند.
         */
        fun unclassified(code: ReasonCode = ReasonCode.NOT_SURE): Verdict =
            Verdict(Category.UNKNOWN, Confidence.LOW, Reason(code))
    }
}

/**
 * خروجی `Router`: پیامک کجا برود و چطور اعلان شود (D29).
 * — واژهٔ پیشنهادی DESIGN-REVIEW: Placement
 */
data class Placement(
    val folder: Folder,
    val notification: NotificationBehavior,
    /** نوار هشدار زرد `Suspect` بالای پیامک (D46). */
    val showWarning: Boolean = false,
    /** لینک‌ها تا تأیید صریح کاربر غیرفعال‌اند (D37). */
    val disableLinks: Boolean = false,
    /**
     * پیامک `Promo` است ولی `LIVE` نبوده، پس خودکار جابه‌جا نمی‌شود و فقط
     * پیشنهاد جابه‌جایی می‌گیرد (اصل ۸، D22).
     */
    val suggestMove: Boolean = false,
    val reason: Reason,
)
