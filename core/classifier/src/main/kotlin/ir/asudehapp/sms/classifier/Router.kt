package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
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
        val allowed = userRules.isAllowed(input.address)
        val blocked = userRules.isBlocked(input.address)
        val hasEvidence = verdict.evidence != null

        // ۱. فیشینگ با شاهد قطعی، از سرشماره‌ای که در فهرست سفید نیست.
        if (verdict.category == Category.PHISHING && hasEvidence && !allowed) {
            return Placement(
                folder = Folder.SCAM,
                notification = NotificationBehavior.NONE,
                showWarning = true,
                disableLinks = true,
                reason = verdict.reason,
            )
        }

        // ۲. همان، ولی سرشماره در فهرست سفید است: پنهان نمی‌شود، ولی `Suspect`
        //    می‌ماند. `LinkGuard` استثنای اصل ۵ است.
        if (verdict.category == Category.PHISHING && hasEvidence) {
            return Placement(
                folder = Folder.INBOX,
                notification = NotificationBehavior.ALERT,
                showWarning = true,
                disableLinks = true,
                reason = verdict.reason,
            )
        }

        // ۳. رمز یکبار و بانکی قطعی، حتی از سرشمارهٔ مسدود (ADR-0006 بند ۲).
        if ((verdict.category == Category.OTP || verdict.category == Category.BANK) &&
            verdict.confidence == Confidence.HIGH
        ) {
            return inbox(verdict)
        }

        // ۴. فهرست سفید کاربر.
        if (allowed) {
            return inbox(verdict, Reason(ReasonCode.ALLOWED_BY_USER))
        }

        // ۵. فهرست سیاه کاربر: برای این سرشماره `Block` بر `ShowOnDoubt` برتری
        //    دارد (ADR-0006 بند ۲).
        if (blocked) {
            return Placement(
                folder = Folder.PROMO,
                notification = NotificationBehavior.NONE,
                reason = Reason(ReasonCode.BLOCKED_BY_USER),
            )
        }

        // قفل ایمنی: پیامک از شمارهٔ موبایل یا مخاطب ذخیره‌شده هرگز خودکار پنهان
        // نمی‌شود (D31). فقط `Block` صریح بالاتر می‌توانست آن را پنهان کند.
        val personalLock =
            input.isKnownContact || Addresses.kindOf(input.address) == SenderKind.MOBILE

        // ۶ و ۷. تبلیغ قطعی.
        if (verdict.category == Category.PROMO && verdict.confidence == Confidence.HIGH && !personalLock) {
            return if (origin == Origin.LIVE) {
                Placement(
                    folder = Folder.PROMO,
                    notification = NotificationBehavior.NONE,
                    reason = verdict.reason,
                )
            } else {
                // فقط پیامکی که اپ خودش زنده گرفته خودکار پنهان می‌شود (اصل ۸، D22).
                inbox(verdict).copy(suggestMove = true)
            }
        }

        // ۸. هر حالت دیگر، از جمله خطای طبقه‌بند: صندوق اصلی (`ShowOnDoubt`).
        return inbox(verdict)
    }

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
