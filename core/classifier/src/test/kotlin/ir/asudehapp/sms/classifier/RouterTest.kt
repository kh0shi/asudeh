package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.DefaultRule
import ir.asudehapp.sms.model.Evidence
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.NotificationBehavior
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.Reason
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.UserRules
import ir.asudehapp.sms.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** جدول تصمیم D30، خط به خط. */
class RouterTest {

    private val adLine = MessageInput("30001234", "…")
    private val mobile = MessageInput("09121234567", "…")

    private fun verdict(
        category: Category,
        confidence: Confidence = Confidence.HIGH,
        evidence: Evidence? = null,
        risk: Boolean = false,
    ) = Verdict(category, confidence, Reason(ReasonCode.NOT_SURE), evidence, risk)

    private val phish = verdict(
        Category.PHISHING,
        evidence = Evidence.DomainSpoof("بانک ملی", "bmi-ir.com", "bmi.ir"),
        risk = true,
    )

    @Test
    fun `phishing with evidence goes to the scam folder without a notification`() {
        val placement = Router.route(phish, adLine)
        assertEquals(Folder.SCAM, placement.folder)
        assertEquals(NotificationBehavior.NONE, placement.notification)
        assertTrue(placement.disableLinks)
    }

    @Test
    fun `phishing from an allowlisted sender stays in the inbox as suspect`() {
        val rules = UserRules(allowlist = setOf("30001234"))
        val placement = Router.route(phish, adLine, rules)
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue("LinkGuard استثنای اصل ۵ است", placement.disableLinks)
        assertTrue(placement.showWarning)
    }

    @Test
    fun `a blocked sender hides even a confident one time password`() {
        // اصلاح ADR-0006 بند ۲: از سرشماره‌ای که کاربر خودش مسدود کرده هیچ
        // پیامکی به صندوق نمی‌آید.
        val rules = UserRules(blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.OTP), adLine, rules)
        assertEquals(Folder.PROMO, placement.folder)
        assertEquals(ReasonCode.BLOCKED_BY_USER, placement.reason.code)
    }

    @Test
    fun `a blocked sender hides even a confident bank message`() {
        val rules = UserRules(blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.BANK), adLine, rules)
        assertEquals(Folder.PROMO, placement.folder)
    }

    @Test
    fun `an allowlisted sender wins over a contradictory block`() {
        val rules = UserRules(allowlist = setOf("30001234"), blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.PROMO), adLine, rules)
        assertEquals(Folder.INBOX, placement.folder)
    }

    @Test
    fun `an allowlisted keyword does not rescue a blocked sender`() {
        val rules = UserRules(blocklist = setOf("30001234"), allowKeywords = setOf("قرار"))
        val message = MessageInput("30001234", "قرار فردا سر جاشه")
        assertEquals(Folder.PROMO, Router.route(verdict(Category.UNKNOWN), message, rules).folder)
    }

    @Test
    fun `an unsure one time password from a blocked sender is hidden`() {
        val rules = UserRules(blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.OTP, Confidence.MEDIUM), adLine, rules)
        assertEquals(Folder.PROMO, placement.folder)
    }

    @Test
    fun `the allowlist keeps confident advertising in the inbox`() {
        val rules = UserRules(allowlist = setOf("30001234"))
        val placement = Router.route(verdict(Category.PROMO), adLine, rules)
        assertEquals(Folder.INBOX, placement.folder)
    }

    @Test
    fun `for a blocked sender block beats show on doubt`() {
        val rules = UserRules(blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.UNKNOWN, Confidence.LOW), adLine, rules)
        assertEquals(Folder.PROMO, placement.folder)
        assertEquals(ReasonCode.BLOCKED_BY_USER, placement.reason.code)
    }

    @Test
    fun `confident live advertising is hidden`() {
        val placement = Router.route(verdict(Category.PROMO), adLine, origin = Origin.LIVE)
        assertEquals(Folder.PROMO, placement.folder)
        assertEquals(NotificationBehavior.NONE, placement.notification)
        assertFalse(placement.suggestMove)
    }

    @Test
    fun `advertising found by the history sweep is only offered for moving`() {
        val placement = Router.route(verdict(Category.PROMO), adLine, origin = Origin.SWEEP)
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue(placement.suggestMove)
    }

    @Test
    fun `advertising added outside the app is only offered for moving`() {
        val placement = Router.route(verdict(Category.PROMO), adLine, origin = Origin.EXTERNAL)
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue(placement.suggestMove)
    }

    @Test
    fun `old phishing found by the sweep stays in the inbox with a warning`() {
        // اصل ۸ و D22: فقط پیامک `LIVE` خودکار جابه‌جا می‌شود.
        for (origin in listOf(Origin.SWEEP, Origin.EXTERNAL)) {
            val placement = Router.route(phish, adLine, origin = origin)
            assertEquals(Folder.INBOX, placement.folder)
            assertTrue(placement.showWarning)
            assertTrue(placement.disableLinks)
            assertTrue(placement.suggestMove)
        }
    }

    @Test
    fun `an old message from a blocked sender is only offered for moving`() {
        val rules = UserRules(blocklist = setOf("30001234"))
        val placement = Router.route(verdict(Category.PROMO), adLine, rules, Origin.EXTERNAL)
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue(placement.suggestMove)
        assertEquals(ReasonCode.BLOCKED_BY_USER, placement.reason.code)
    }

    @Test
    fun `a message from a mobile number is never hidden automatically`() {
        val placement = Router.route(verdict(Category.PROMO), mobile)
        assertEquals(Folder.INBOX, placement.folder)
    }

    @Test
    fun `a message from a saved contact is never hidden automatically`() {
        val contact = MessageInput("6655", "…", isKnownContact = true)
        val placement = Router.route(verdict(Category.PROMO), contact)
        assertEquals(Folder.INBOX, placement.folder)
    }

    @Test
    fun `an explicit block still hides a personal number`() {
        val rules = UserRules(blocklist = setOf("09121234567"))
        val placement = Router.route(verdict(Category.PROMO), mobile, rules)
        assertEquals(Folder.PROMO, placement.folder)
    }

    @Test
    fun `anything the classifier is unsure about stays in the inbox`() {
        val placement = Router.route(verdict(Category.UNKNOWN, Confidence.LOW), adLine)
        assertEquals(Folder.INBOX, placement.folder)
    }

    @Test
    fun `service messages are notified silently`() {
        val placement = Router.route(verdict(Category.SERVICE, Confidence.MEDIUM), adLine)
        assertEquals(NotificationBehavior.SILENT, placement.notification)
    }

    @Test
    fun `the allowlist matches a number written in international form`() {
        val rules = UserRules(allowlist = setOf("+989121234567"))
        val placement = Router.route(verdict(Category.PROMO), mobile, rules)
        assertEquals(Folder.INBOX, placement.folder)
        assertEquals(ReasonCode.ALLOWED_BY_USER, placement.reason.code)
    }

    // ——— کلیدواژه‌ها و قواعد پیش‌فرض (ADR-0012) ———

    @Test
    fun `a blocklisted keyword hides a message that would have stayed in the inbox`() {
        val rules = UserRules(blockKeywords = setOf("تخفیف"))
        val message = MessageInput("30001234", "امروز ۳۰٪ تخفیف ویژه")
        val placement = Router.route(verdict(Category.UNKNOWN, Confidence.LOW), message, rules)
        assertEquals(Folder.PROMO, placement.folder)
        assertEquals(ReasonCode.BLOCKED_KEYWORD, placement.reason.code)
        assertEquals("تخفیف", placement.reason.args.single())
    }

    @Test
    fun `a keyword ignores spaces and persian digits`() {
        val rules = UserRules(blockKeywords = setOf("لغو ۱۱"))
        val message = MessageInput("30001234", "فروش ویژه. لغو11")
        assertEquals(Folder.PROMO, Router.route(verdict(Category.UNKNOWN, Confidence.LOW), message, rules).folder)
    }

    @Test
    fun `an allowlisted keyword keeps a promo message in the inbox`() {
        val rules = UserRules(allowKeywords = setOf("کد ورود"))
        val message = MessageInput("30001234", "کدورود شما ۴۳۲۱ است")
        val placement = Router.route(verdict(Category.PROMO), message, rules)
        assertEquals(Folder.INBOX, placement.folder)
        assertEquals(ReasonCode.ALLOWED_KEYWORD, placement.reason.code)
    }

    @Test
    fun `a keyword never hides a confident one time password`() {
        val rules = UserRules(blockKeywords = setOf("تخفیف"))
        val message = MessageInput("30001234", "رمز ورود شما ۱۲۳۴ است. کد تخفیف ندارید.")
        assertEquals(Folder.INBOX, Router.route(verdict(Category.OTP), message, rules).folder)
    }

    @Test
    fun `an old message caught by a keyword is only suggested`() {
        val rules = UserRules(blockKeywords = setOf("تخفیف"))
        val message = MessageInput("30001234", "۳۰٪ تخفیف")
        val placement = Router.route(
            verdict(Category.UNKNOWN, Confidence.LOW),
            message,
            rules,
            Origin.SWEEP,
        )
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue(placement.suggestMove)
    }

    @Test
    fun `a blocklisted keyword never hides a message from a contact`() {
        val rules = UserRules(blockKeywords = setOf("تخفیف"))
        val contact = MessageInput("09121234567", "این مغازه تخفیف خوبی داشت", isKnownContact = true)
        assertEquals(Folder.INBOX, Router.route(verdict(Category.UNKNOWN, Confidence.LOW), contact, rules).folder)
    }

    @Test
    fun `an explicit block beats the contact lock even for a keyword rule`() {
        val rules = UserRules(blocklist = setOf("09121234567"), blockKeywords = setOf("تخفیف"))
        val contact = MessageInput("09121234567", "تخفیف", isKnownContact = true)
        assertEquals(Folder.PROMO, Router.route(verdict(Category.UNKNOWN, Confidence.LOW), contact, rules).folder)
    }

    @Test
    fun `turning off promo hiding keeps a certain advertisement in the inbox`() {
        val rules = UserRules(disabledDefaults = setOf(DefaultRule.HIDE_PROMO))
        assertEquals(Folder.INBOX, Router.route(verdict(Category.PROMO), adLine, rules).folder)
    }

    @Test
    fun `turning off scam hiding keeps the warning but not the folder`() {
        val rules = UserRules(disabledDefaults = setOf(DefaultRule.HIDE_SCAM))
        val placement = Router.route(phish, adLine, rules)
        assertEquals(Folder.INBOX, placement.folder)
        assertTrue(placement.showWarning)
        assertTrue(placement.disableLinks)
    }

    @Test
    fun `turning off the contact rule lets an advertisement from a contact be hidden`() {
        val contact = MessageInput("09121234567", "…", isKnownContact = true)
        assertEquals(
            "مخاطب به‌طور پیش‌فرض در فهرست سفید است",
            Folder.INBOX,
            Router.route(verdict(Category.PROMO), contact).folder,
        )
        val rules = UserRules(
            disabledDefaults = setOf(DefaultRule.CONTACTS_IN_ALLOWLIST, DefaultRule.MOBILE_NEVER_HIDDEN),
        )
        assertEquals(Folder.PROMO, Router.route(verdict(Category.PROMO), contact, rules).folder)
    }

    @Test
    fun `turning off the otp rule lets the blocklist hide a code`() {
        val rules = UserRules(
            blocklist = setOf("30001234"),
            disabledDefaults = setOf(DefaultRule.OTP_AND_BANK_ALWAYS_INBOX),
        )
        assertEquals(Folder.PROMO, Router.route(verdict(Category.OTP), adLine, rules).folder)
    }
}
