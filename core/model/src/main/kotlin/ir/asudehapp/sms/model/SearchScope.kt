package ir.asudehapp.sms.model

/** فیلتر نتیجهٔ جستجو (ROADMAP E6). */
enum class SearchKind {
    ALL,

    /** پیامک‌های پیوست‌دار: تصویر، ویدیو، صدا یا کارت مخاطب. */
    ATTACHMENTS,
    OTP,
    BANK,

    /** پیامک‌های پوشه‌های پنهان (تبلیغات و کلاهبرداری). */
    HIDDEN,
}

/**
 * دامنهٔ جستجو (ROADMAP E6): نوع پیامک، یک فرستنده، یا یک گفتگو. هیچ‌کدام از
 * این‌ها وارد عبارت `MATCH` نمی‌شود؛ همه پارامتر جدای SQL‌اند، پس متن کاربر
 * (از جمله سرشمارهٔ فرستنده) نمی‌تواند عملگر FTS بسازد. خود متن جستجو فقط از
 * `SearchText.ftsQuery` می‌گذرد.
 */
data class SearchScope(
    val kind: SearchKind = SearchKind.ALL,
    /** سرشماره‌ای که کاربر نوشته؛ خالی یعنی همهٔ فرستنده‌ها. */
    val sender: String = "",
    /** فقط همین گفتگو؛ صفر یعنی همهٔ گفتگوها. */
    val threadId: Long = 0,
) {
    /** دامنه چیزی را کنار می‌گذارد؛ آن‌وقت جستجوی بی‌متن هم نتیجه دارد. */
    val narrowed: Boolean get() = kind != SearchKind.ALL || senderKey.isNotEmpty() || threadId > 0

    /** سرشمارهٔ یکسان‌شده، همان `normalizedAddress` ایندکس. */
    val senderKey: String get() = if (sender.isBlank()) "" else Addresses.normalize(sender)

    /** دسته‌ها به شکل `,OTP,` برای `instr` در SQL؛ خالی یعنی همه. */
    val categories: String get() = when (kind) {
        SearchKind.OTP -> ",${Category.OTP.name},"
        SearchKind.BANK -> ",${Category.BANK.name},"
        else -> ""
    }

    val onlyAttachments: Boolean get() = kind == SearchKind.ATTACHMENTS

    val onlyHidden: Boolean get() = kind == SearchKind.HIDDEN
}
