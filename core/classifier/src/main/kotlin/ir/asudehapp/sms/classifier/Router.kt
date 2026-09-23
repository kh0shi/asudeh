package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.DefaultRule
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.NotificationBehavior
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.Placement
import ir.asudehapp.sms.model.Reason
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.SenderKind
import ir.asudehapp.sms.model.UserRules
import ir.asudehapp.sms.model.Verdict
import ir.asudehapp.sms.persian.KeywordMatch

/**
 * «این پیامک کجا برود». همهٔ قواعد «چه کسی بر چه کسی برتری دارد» در همین یک
 * تابع خالص جمع شده‌اند (D29)، به همان ترتیب جدول D30.
 */
object Router {

    fun route(
        verdict: Verdict,
        input: MessageInput,
        userRules: UserRules = UserRules.EMPTY,
        origin: Origin = Origin.LIVE,
    ): Placement {
        // کلیدواژه‌های «قواعد من» و کلیدواژه‌های پیش‌فرضِ خاموش‌نشده، یک‌جا
        // (ADR-0012). متن فقط وقتی آماده می‌شود که کلیدواژه‌ای در کار باشد.
        val keyedBody = if (userRules.hasKeywords) KeywordMatch.key(input.body) else ""
        val allowKeyword = userRules.allowKeywordIn(keyedBody)
        val blockKeyword = userRules.blockKeywordIn(keyedBody)

        // قفل ایمنی: پیامک از شمارهٔ موبایل یا مخاطب ذخیره‌شده هرگز خودکار پنهان
        // نمی‌شود (D31). «مخاطب‌ها در فهرست سفیدند» یعنی همین، و کلیدواژهٔ فهرست
        // سیاه هم آن را نمی‌شکند: پیام دوستی که «تخفیف» نوشته، تبلیغ نیست. هر
        // دو قاعده در «قواعد من» خاموش‌شدنی‌اند (ADR-0012). فقط `Block` صریحِ
        // خود آن سرشماره از این قفل می‌گذرد.
        val personalLock =
            (input.isKnownContact && userRules.isOn(DefaultRule.CONTACTS_IN_ALLOWLIST)) ||
                (
                    Addresses.kindOf(input.address) == SenderKind.MOBILE &&
                        userRules.isOn(DefaultRule.MOBILE_NEVER_HIDDEN)
                    )

        val allowedSender = userRules.isAllowed(input.address)

        // `Block` صریحِ یک سرشماره حرف آخر را می‌زند: از سرشماره‌ای که کاربر
        // مسدود کرده، هیچ پیامکی به صندوق نمی‌آید — نه رمز یکبار، نه پیامک
        // بانکی، نه پیامکی که کلیدواژهٔ فهرست سفید دارد (ADR-0006 بند ۲ را
        // اصلاح می‌کند). `Allowlist` صریحِ همان سرشماره تنها چیزی است که از آن
        // می‌گذرد، تا پیکربندی متناقض کاربر را در بن‌بست نگذارد.
        val blockedSender = userRules.isBlocked(input.address) && !allowedSender

        val allowed = allowedSender || (allowKeyword != null && !blockedSender)
        val blocked = blockedSender || (blockKeyword != null && !personalLock)
        val hasEvidence = verdict.evidence != null

        // ۱. فیشینگ با شاهد قطعی، از سرشماره‌ای که در فهرست سفید نیست.
        //    فقط پیامک `LIVE` خودکار جابه‌جا می‌شود؛ پیامک قدیمی با هشدار در
        //    صندوق می‌ماند و فقط پیشنهاد جابه‌جایی می‌گیرد (اصل ۸، D22).
        if (verdict.category == Category.PHISHING && hasEvidence && !allowed &&
            userRules.isOn(DefaultRule.HIDE_SCAM)
        ) {
            if (origin != Origin.LIVE) return suggested(verdict)
            return Placement(
                folder = Folder.SCAM,
                notification = NotificationBehavior.NONE,
                showWarning = true,
                disableLinks = true,
                reason = verdict.reason,
            )
        }

        // ۲. همان، ولی سرشماره در فهرست سفید است یا کاربر پنهان کردن
        //    کلاهبرداری را خاموش کرده: پنهان نمی‌شود، ولی `Suspect` می‌ماند.
        //    `LinkGuard` استثنای اصل ۵ است.
        if (verdict.category == Category.PHISHING && hasEvidence) {
            return Placement(
                folder = Folder.INBOX,
                notification = NotificationBehavior.ALERT,
                showWarning = true,
                disableLinks = true,
                reason = verdict.reason,
            )
        }

        // ۳. رمز یکبار و بانکی قطعی، حتی وقتی کلیدواژهٔ فهرست سیاه در متنشان
        //    هست. ولی نه از سرشماره‌ای که کاربر خودش مسدود کرده است: آنجا
        //    خواستهٔ صریح کاربر مقدم است (اصل ۵).
        if ((verdict.category == Category.OTP || verdict.category == Category.BANK) &&
            verdict.confidence == Confidence.HIGH && !blockedSender &&
            userRules.isOn(DefaultRule.OTP_AND_BANK_ALWAYS_INBOX)
        ) {
            return inbox(verdict)
        }

        // ۴. فهرست سفید کاربر: سرشماره یا کلیدواژه.
        if (allowed) {
            val reason = if (allowedSender) {
                Reason(ReasonCode.ALLOWED_BY_USER)
            } else {
                Reason(ReasonCode.ALLOWED_KEYWORD, listOf(allowKeyword.orEmpty()))
            }
            return inbox(verdict, reason)
        }

        // ۵. فهرست سیاه کاربر: برای این سرشماره `Block` بر `ShowOnDoubt` برتری
        //    دارد (ADR-0006 بند ۲).
        if (blocked) {
            val reason = if (blockKeyword != null && !blockedSender) {
                Reason(ReasonCode.BLOCKED_KEYWORD, listOf(blockKeyword))
            } else {
                Reason(ReasonCode.BLOCKED_BY_USER)
            }
            if (origin != Origin.LIVE) return suggested(verdict, reason)
            return Placement(
                folder = Folder.PROMO,
                notification = NotificationBehavior.NONE,
                reason = reason,
            )
        }

        // ۶ و ۷. تبلیغ قطعی.
        if (verdict.category == Category.PROMO && verdict.confidence == Confidence.HIGH &&
            !personalLock && userRules.isOn(DefaultRule.HIDE_PROMO)
        ) {
            return if (origin == Origin.LIVE) {
                Placement(
                    folder = Folder.PROMO,
                    notification = NotificationBehavior.NONE,
                    reason = verdict.reason,
                )
            } else {
                // فقط پیامکی که اپ خودش زنده گرفته خودکار پنهان می‌شود (اصل ۸، D22).
                suggested(verdict)
            }
        }

        // ۸. هر حالت دیگر، از جمله خطای طبقه‌بند: صندوق اصلی (`ShowOnDoubt`).
        return inbox(verdict)
    }

    /**
     * پیامکی که اگر زنده رسیده بود پنهان می‌شد، ولی چون `origin` آن `LIVE` نیست
     * در صندوق می‌ماند و فقط پیشنهاد جابه‌جایی می‌گیرد (اصل ۸، D22). هشدار و
     * غیرفعال بودن لینک‌ها سر جایش می‌ماند.
     */
    private fun suggested(verdict: Verdict, reason: Reason = verdict.reason) =
        inbox(verdict, reason).copy(suggestMove = true)

    private fun inbox(verdict: Verdict, reason: Reason = verdict.reason) = Placement(
        folder = Folder.INBOX,
        notification = notificationFor(verdict),
        showWarning = verdict.risk,
        disableLinks = verdict.risk,
        reason = reason,
    )

    /** کانال‌ها و پیش‌فرض‌های D38. */
    fun notificationFor(verdict: Verdict): NotificationBehavior = when {
        verdict.risk -> NotificationBehavior.ALERT
        verdict.category == Category.SERVICE -> NotificationBehavior.SILENT
        verdict.category == Category.PROMO || verdict.category == Category.PHISHING ->
            NotificationBehavior.SILENT

        else -> NotificationBehavior.ALERT
    }
}
