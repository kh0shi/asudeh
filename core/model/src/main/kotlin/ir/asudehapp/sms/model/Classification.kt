package ir.asudehapp.sms.model

/**
 * برچسب معنایی پیامک؛ خروجی `Classifier`. رفتار را تعیین می‌کند، نه محل را
 * (به‌جز `Promo` و `Phish` که `Router` آن‌ها را جابه‌جا می‌کند). — GLOSSARY: Category
 */
enum class Category {
    PERSONAL,
    OTP,
    BANK,
    SERVICE,
    PROMO,
    PHISHING,

    /** طبقه‌بند به نتیجه‌ای نرسیده است. طبق `ShowOnDoubt` در `Inbox` می‌ماند. */
    UNKNOWN,
}

/** محل نمایش هر پیامک. فقط سه تا داریم (D7). `Folder` مال پیامک است، نه گفتگو (ADR-0005). */
enum class Folder {
    INBOX,
    PROMO,
    SCAM,
}

/** منشأ ورود پیامک به اپ. فقط `LIVE` خودکار پنهان می‌شود (D22). */
enum class Origin {
    /** اپ خودش پیامک را زنده دریافت کرده است. */
    LIVE,

    /** `HistorySweep`: پیامک قدیمی که در اولین اجرا پیدا شده است. */
    SWEEP,

    /** پیامکی که بیرون از اپ به provider اضافه شده است. */
    EXTERNAL,
}

/** اطمینان طبقه‌بند. جدول D30 فقط به «اطمینان بالا» حساس است. */
enum class Confidence {
    LOW,
    MEDIUM,
    HIGH,
}

/** رفتار اعلان برای یک پیامک (D38). */
enum class NotificationBehavior {
    /** صدا و لرزش. */
    ALERT,

    /** اعلان بی‌صدا. */
    SILENT,

    /** بدون اعلان. */
    NONE,
}

/** نوع سرشماره‌ای که پیامک از آن آمده است. */
enum class SenderKind {
    /** خط انبوه ایرانی (`1000…`، `2000…`، `3000…`، `5000…`، `9000…`). — GLOSSARY: AdLine */
    AD_LINE,

    /** سرشمارهٔ کوتاه که `AdLine` نیست. */
    SHORT_CODE,

    /** شمارهٔ موبایل ایرانی (`09xx` یا `+989xx`). */
    MOBILE,

    /** شمارهٔ بین‌المللی یا ثابت. */
    OTHER_NUMBER,

    /** فرستندهٔ حرفی (alphanumeric) یا ناشناس. */
    ALPHANUMERIC,
}
