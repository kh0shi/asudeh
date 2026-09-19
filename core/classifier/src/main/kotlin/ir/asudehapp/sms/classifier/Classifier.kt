package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.DetectedLink
import ir.asudehapp.sms.model.Evidence
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.Reason
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.SenderKind
import ir.asudehapp.sms.model.Verdict
import ir.asudehapp.sms.persian.PersianText

/**
 * «این پیامک چیست». تابع خالص است: فقط محتوا و سرشماره را می‌بیند و هیچ اطلاعی
 * از `Allowlist`، `Blocklist` یا تنظیمات کاربر ندارد (D29). تصمیم «کجا برود»
 * کار [Router] است.
 */
class Classifier(private val rules: CompiledRulePack) {

    constructor() : this(CompiledRulePack.bundled())

    fun classify(input: MessageInput): Verdict {
        val text = PersianText.normalize(input.body)
        val address = Addresses.normalize(input.address)
        val senderKind = Addresses.kindOf(input.address)
        val links = LinkGuard.extract(input.body)

        // ۱. فیشینگ، فقط با شاهد ساختاری (اصل ۴). هیچ امتیاز احتمالی اینجا دخالت نمی‌کند.
        phishing(text, address, senderKind, input.isKnownContact, links)?.let { return it }

        // ۲. رمز یکبار مصرف: مستقل از هر چیز دیگری تشخیص داده می‌شود (ADR-0006).
        //    لینک مشکوک در پیامک رمز یکبار یا بانکی هم هشدار می‌گیرد؛ این دقیقاً
        //    الگوی رایج پیامک جعلی بانکی است (`LinkGuard`، اصل ۵ استثنا).
        otp(text, links)?.let { return it.withRisk(text, links) }

        // ۳. بانکی
        bank(text, links)?.let { return it.withRisk(text, links) }

        // ۴. تبلیغاتی
        promo(text, senderKind)?.let { return it.withRisk(text, links) }

        // ۵. خدماتی
        if (rules.service.matches(text)) {
            return Verdict(
                category = Category.SERVICE,
                confidence = Confidence.MEDIUM,
                reason = Reason(ReasonCode.SERVICE_PATTERN, listOfNotNull(rules.service.firstHit(text))),
                links = links,
            ).withRisk(text, links)
        }

        // ۶. پیامک از آدم واقعی
        if (senderKind == SenderKind.MOBILE || input.isKnownContact) {
            return Verdict(
                category = Category.PERSONAL,
                confidence = Confidence.MEDIUM,
                reason = Reason(
                    if (input.isKnownContact) ReasonCode.KNOWN_CONTACT else ReasonCode.PERSONAL_NUMBER,
                ),
                links = links,
            ).withRisk(text, links)
        }

        // ۷. در شک، نشان بده (اصل ۲).
        return Verdict(
            category = Category.UNKNOWN,
            confidence = Confidence.LOW,
            reason = Reason(ReasonCode.NOT_SURE),
            links = links,
        ).withRisk(text, links)
    }

    private fun phishing(
        text: String,
        address: String,
        senderKind: SenderKind,
        isKnownContact: Boolean,
        links: List<DetectedLink>,
    ): Verdict? {
        for (brand in rules.brands) {
            if (!brand.isMentionedIn(text)) continue

            // لینکی که خودش دامنهٔ رسمی همین نهاد است (مثلاً mymci.ir برای همراه
            // اول) جعل نیست، حتی اگر شبیه دامنهٔ رسمی دیگرِ همان نهاد باشد.
            val offBrandLinks = links.filterNot { brand.isOfficialHost(it.host) }

            // جعل دامنه: پیامک از نهاد X حرف می‌زند و لینکی دارد که شبیه دامنهٔ
            // رسمی X است ولی خودش نیست.
            for (link in offBrandLinks) {
                for (official in brand.domains) {
                    if (LinkGuard.isLookalike(link.host, official)) {
                        return phishVerdict(
                            Evidence.DomainSpoof(brand.brand.name, link.displayHost, official),
                            ReasonCode.DOMAIN_SPOOF,
                            listOf(brand.brand.name, link.displayHost, official),
                            links,
                        )
                    }
                }
            }

            // جعل سرشماره: نهادی که سرشمارهٔ رسمی دارد، از یک خط شخصی پیامک
            // با لینک غیررسمی نمی‌فرستد. دوستی که لینک رسمی بانک را فرستاده، یا
            // مخاطب ذخیره‌شده، جعل نیست (D31).
            if (offBrandLinks.isNotEmpty() && !isKnownContact &&
                brand.senders.isNotEmpty() && !brand.isOfficialSender(address)
            ) {
                if (senderKind == SenderKind.MOBILE || senderKind == SenderKind.ALPHANUMERIC) {
                    return phishVerdict(
                        Evidence.SenderSpoof(brand.brand.name, address),
                        ReasonCode.SENDER_SPOOF,
                        listOf(brand.brand.name, address),
                        links,
                    )
                }
            }
        }
        return null
    }

    private fun phishVerdict(
        evidence: Evidence,
        code: ReasonCode,
        args: List<String>,
        links: List<DetectedLink>,
    ) = Verdict(
        category = Category.PHISHING,
        confidence = Confidence.HIGH,
        reason = Reason(code, args),
        evidence = evidence,
        risk = true,
        links = links,
    )

    private fun otp(text: String, links: List<DetectedLink>): Verdict? {
        if (!rules.otp.matches(text)) return null
        val hasCode = OTP_CODE.containsMatchIn(text)
        return Verdict(
            category = Category.OTP,
            confidence = if (hasCode) Confidence.HIGH else Confidence.MEDIUM,
            reason = Reason(ReasonCode.OTP_PATTERN, listOfNotNull(rules.otp.firstHit(text))),
            links = links,
        )
    }

    private fun bank(text: String, links: List<DetectedLink>): Verdict? {
        val bankHits = rules.bank.hits(text)
        if (bankHits == 0) return null
        val hasAmount = rules.amount.matches(text)
        val promoStrong = rules.promoStrong.hits(text)
        val transaction = rules.bankTransaction.matches(text)

        // تبلیغ وام و بیمه، یا تبلیغی که قیمت دارد، «بانکی» نیست. فقط نشانهٔ
        // قطعی تراکنش آن را بانکی می‌کند، و حتی آن‌وقت هم اطمینان بالا نمی‌گیرد
        // تا نتواند از `Blocklist` رد شود (ADR-0006 بند ۲).
        if (promoStrong > 0 && !transaction) return null

        val confident = hasAmount && transaction && bankHits >= 2 && promoStrong == 0
        return Verdict(
            category = Category.BANK,
            confidence = if (confident) Confidence.HIGH else Confidence.MEDIUM,
            reason = Reason(ReasonCode.BANK_PATTERN, listOfNotNull(rules.bank.firstHit(text))),
            links = links,
        )
    }

    private fun promo(text: String, senderKind: SenderKind): Verdict? {
        val strong = rules.promoStrong.hits(text)
        val weak = rules.promo.hits(text)
        val score = strong * 2 + weak
        val bayes = bayesScore(text)
        val bayesSaysPromo = bayes >= rules.bayes.threshold

        if (score == 0 && !bayesSaysPromo) return null

        val isAdLine = senderKind == SenderKind.AD_LINE
        val confidence = when {
            // Bayes به‌تنهایی فقط برای خطوط انبوه می‌تواند پیامکی را پنهان کند (D32 ب).
            isAdLine && (score >= 2 || bayesSaysPromo) -> Confidence.HIGH
            score >= 4 -> Confidence.HIGH
            score >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val code = if (isAdLine) ReasonCode.AD_LINE_AND_PROMO_WORDS else ReasonCode.PROMO_WORDS
        val hit = rules.promoStrong.firstHit(text) ?: rules.promo.firstHit(text)
        return Verdict(
            category = Category.PROMO,
            confidence = confidence,
            reason = Reason(code, listOfNotNull(hit)),
        )
    }

    /** مدل از پیش آموزش‌دیده؛ روی گوشی آموزش نمی‌بیند (D32 ب). */
    internal fun bayesScore(normalizedText: String): Double {
        var score = rules.bayes.bias
        for ((token, weight) in rules.bayes.weights) {
            if (normalizedText.contains(token)) score += weight
        }
        return score
    }

    /**
     * نشانهٔ مشکوک بدون `Evidence` (اصل ۴). پیامک در `Inbox` می‌ماند، ولی با
     * هشدار و بدون لینک فعال.
     */
    private fun Verdict.withRisk(text: String, links: List<DetectedLink>): Verdict {
        if (risk) return this
        val baitedLink = links.isNotEmpty() && rules.scamBait.matches(text)
        val shortened = links.any { LinkGuard.registrableDomain(it.host) in rules.shorteners }
        val disguised = links.any { it.isPunycode || it.hasConfusableChars }
        if (!baitedLink && !shortened && !disguised) return this
        return copy(
            risk = true,
            reason = Reason(ReasonCode.SUSPICIOUS_LINK, listOfNotNull(links.firstOrNull()?.displayHost)),
            links = links,
        )
    }

    private companion object {
        /** کد چهار تا هشت رقمی که در پیامک رمز یکبار مصرف هست. */
        val OTP_CODE = Regex("""(?<!\d)\d{4,8}(?!\d)""")
    }
}
