package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.Evidence
import ir.asudehapp.sms.model.MessageInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیامک‌های این آزمون همه دست‌نویس‌اند و از پیامک واقعی کسی برداشته نشده‌اند
 * (D34). پیکرهٔ واقعی جای دیگری نگه داشته می‌شود.
 */
class ClassifierTest {

    private val classifier = Classifier()

    private fun classify(address: String, body: String, contact: Boolean = false) =
        classifier.classify(MessageInput(address, body, isKnownContact = contact))

    @Test
    fun `one time password is recognised with high confidence`() {
        val verdict = classify("10001", "رمز یکبار مصرف شما ۴۵۸۲۱ است. بانک ملت")
        assertEquals(Category.OTP, verdict.category)
        assertEquals(Confidence.HIGH, verdict.confidence)
    }

    @Test
    fun `one time password wins over an advertising short code`() {
        val verdict = classify("30001234", "کد تایید شما: ۹۹۱۲۳ - فروشگاه")
        assertEquals(Category.OTP, verdict.category)
    }

    @Test
    fun `bank transaction is recognised with high confidence`() {
        val verdict = classify(
            "200011",
            "بانک ملی\nبرداشت ۱٬۵۰۰٬۰۰۰ ریال\nمانده: ۱۲٬۳۰۰٬۰۰۰ ریال",
        )
        assertEquals(Category.BANK, verdict.category)
        assertEquals(Confidence.HIGH, verdict.confidence)
    }

    @Test
    fun `a loan advertisement from a bank short code is not a bank message`() {
        val verdict = classify("20002030", "وام فوری بدون ضامن با تخفیف ویژه! همین حالا تماس بگیرید")
        assertEquals(Category.PROMO, verdict.category)
    }

    @Test
    fun `advertising from a bulk line is recognised with high confidence`() {
        val verdict = classify(
            "30001234",
            "جشنواره فروش ویژه! تا ۵۰٪ تخفیف روی همه محصولات. لغو۱۱",
        )
        assertEquals(Category.PROMO, verdict.category)
        assertEquals(Confidence.HIGH, verdict.confidence)
    }

    @Test
    fun `advertising words from a mobile number stay below high confidence unless strong`() {
        val verdict = classify("09121234567", "سلام، فردا میای؟ یه کد تخفیف برات دارم")
        assertEquals(Category.PROMO, verdict.category)
        assertFalse(
            "قفل ایمنی D31 در Router است، ولی طبقه‌بند هم نباید از یک کلمه مطمئن شود",
            verdict.confidence == Confidence.HIGH,
        )
    }

    @Test
    fun `a plain message from a mobile number is personal`() {
        val verdict = classify("09351112233", "سلام، رسیدم خونه. فردا میبینمت")
        assertEquals(Category.PERSONAL, verdict.category)
    }

    @Test
    fun `a saved contact is personal even from a short code`() {
        val verdict = classify("4040", "قرار فردا سر جاشه", contact = true)
        assertEquals(Category.PERSONAL, verdict.category)
    }

    @Test
    fun `service messages are recognised`() {
        val verdict = classify("200030", "مرسوله شما با کد رهگیری ۱۲۳۴۵۶۷۸ ارسال شد")
        assertEquals(Category.SERVICE, verdict.category)
    }

    @Test
    fun `a faked bank domain is phishing with evidence`() {
        val verdict = classify(
            "09121234567",
            "بانک ملی: حساب شما مسدود خواهد شد. برای رفع مسدودی وارد شوید bmi-ir.com/login",
        )
        assertEquals(Category.PHISHING, verdict.category)
        assertEquals(Confidence.HIGH, verdict.confidence)
        assertTrue(verdict.evidence is Evidence.DomainSpoof)
    }

    @Test
    fun `an official brand writing from a personal line with a link is phishing`() {
        val verdict = classify(
            "09351112233",
            "قوه قضاییه: ابلاغیه قضایی جدید دارید. مشاهده: adl-notice.click",
        )
        assertEquals(Category.PHISHING, verdict.category)
        assertTrue(verdict.evidence is Evidence.SenderSpoof)
    }

    @Test
    fun `the official domain of a brand is never phishing`() {
        val verdict = classify(
            "200011",
            "بانک ملی: برای فعال‌سازی رمز پویا به bmi.ir مراجعه کنید",
        )
        assertNull(verdict.evidence)
        assertTrue(verdict.category != Category.PHISHING)
    }

    @Test
    fun `a subdomain of the official domain is never phishing`() {
        val verdict = classify(
            "200030",
            "پست ایران: مرسوله شما را در tracking.post.ir پیگیری کنید",
        )
        assertNull(verdict.evidence)
    }

    @Test
    fun `bait plus a shortened link is suspect but not phishing`() {
        val verdict = classify(
            "50004321",
            "شما برنده شدید! جایزه شما آماده است، کلیک کنید bit.ly/abcd",
        )
        assertNull("بدون شاهد ساختاری نباید فیشینگ اعلام شود", verdict.evidence)
        assertTrue("باید Suspect شود", verdict.risk)
    }

    @Test
    fun `an unremarkable message from an unknown short code stays unknown`() {
        val verdict = classify("6655", "اطلاعیه: ساعت کاری روز پنجشنبه تغییر کرده است")
        assertEquals(Category.UNKNOWN, verdict.category)
        assertEquals(Confidence.LOW, verdict.confidence)
    }

    @Test
    fun `an operator's own second official domain is not a spoof`() {
        val mci = classify("9990", "همراه اول: بسته اینترنت را از mymci.ir بخرید")
        assertTrue(mci.category != Category.PHISHING)
        val irancell = classify("7575", "ایرانسل: فاکتور خود را در mtnirancell.ir ببینید")
        assertTrue(irancell.category != Category.PHISHING)
    }

    @Test
    fun `a friend forwarding the official bank link is not a sender spoof`() {
        val verdict = classify("09121234567", "بانک ملی رو دیدی؟ این لینکش bmi.ir", contact = true)
        assertTrue(verdict.category != Category.PHISHING)
        val stranger = classify("09121234567", "بانک ملی: وارد bmi.ir شوید")
        assertTrue("لینک رسمی، جعل سرشماره نیست", stranger.category != Category.PHISHING)
    }

    @Test
    fun `a latin brand abbreviation inside another word is not a mention`() {
        val verdict = classify("09121234567", "please submit the form at docs.google.com")
        assertTrue(verdict.category != Category.PHISHING)
    }

    @Test
    fun `a stranger mentioning a bank with an unofficial link is still a sender spoof`() {
        val verdict = classify("09121234567", "بانک ملی: حساب شما مسدود شد. وارد melli-pay.com شوید")
        assertEquals(Category.PHISHING, verdict.category)
    }

    @Test
    fun `a one time password with a bait link is suspect`() {
        val verdict = classify("10001", "کد تایید شما 48213 است. برای دریافت جایزه وارد bit.ly/abc شوید")
        assertEquals(Category.OTP, verdict.category)
        assertTrue("لینک کوتاه‌شده در پیامک رمز یکبار باید هشدار بگیرد", verdict.risk)
    }

    @Test
    fun `a bank message with a bait link is suspect`() {
        val verdict = classify("200011", "برداشت 1,500,000 ریال از حساب شما. برای لغو کلیک کنید bit.ly/x1")
        assertEquals(Category.BANK, verdict.category)
        assertTrue(verdict.risk)
    }

    @Test
    fun `a sale with a price is not a bank message`() {
        val verdict = classify("30001234", "جشنواره فروش! کالای کوچک فقط 250,000 تومان، 3 روز باقی مانده")
        assertEquals(Category.PROMO, verdict.category)
        val installments = classify("30001234", "فروش اقساطی بدون ضامن، قیمت 12,000,000 تومان")
        assertTrue(installments.category != Category.BANK)
    }

    @Test
    fun `a bank message that also advertises never gets high confidence`() {
        val verdict = classify("200011", "واریز 5,000,000 ریال به حساب شما. جشنواره تخفیف ویژه!")
        assertEquals(Category.BANK, verdict.category)
        assertEquals(Confidence.MEDIUM, verdict.confidence)
    }

    @Test
    fun `invisible characters do not hide promotional words`() {
        val verdict = classify("30001234", "ت\u200Bخفیف ویژه ح\u200Bراج")
        assertEquals(Category.PROMO, verdict.category)
        assertEquals(Confidence.HIGH, verdict.confidence)
    }

    @Test
    fun `classifying one message stays well under five milliseconds`() {
        val samples = listOf(
            "10001" to "رمز یکبار مصرف شما ۴۵۸۲۱ است",
            "30001234" to "جشنواره فروش ویژه! تا ۵۰٪ تخفیف. لغو۱۱",
            "09121234567" to "سلام، فردا میبینمت",
            "200011" to "بانک ملی\nبرداشت ۱٬۵۰۰٬۰۰۰ ریال\nمانده: ۱۲٬۳۰۰٬۰۰۰ ریال",
        )
        repeat(200) { samples.forEach { (address, body) -> classify(address, body) } }

        val started = System.nanoTime()
        val rounds = 500
        repeat(rounds) { samples.forEach { (address, body) -> classify(address, body) } }
        val perMessageMillis =
            (System.nanoTime() - started) / 1_000_000.0 / (rounds * samples.size)

        assertTrue("زمان طبقه‌بندی هر پیامک: $perMessageMillis میلی‌ثانیه", perMessageMillis < 5.0)
    }
}
