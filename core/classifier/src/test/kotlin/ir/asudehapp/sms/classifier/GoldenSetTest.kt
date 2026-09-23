package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.MessageInput
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * معیارهای پذیرش طبقه‌بند روی `GoldenSet` (D34، D35). این آزمون همان چیزی
 * است که `core` job در CI اجرا می‌کند (`:core:classifier:test`)، پس شکست هر
 * کدام از معیارهای زیر انتشار را متوقف می‌کند.
 *
 * عملیاتی‌سازی «`FalsePos`» روی `Personal`/`OTP`/`Bank`/`Service` (D35):
 * طبق `Router.kt`، تنها دو دسته‌اند که خودکار از صندوق پنهان می‌شوند —
 * `Promo` (به پوشهٔ تبلیغات) و `Phishing` با شاهد (به پوشهٔ کلاهبرداری؛ در
 * `Classifier` هر `Category.PHISHING` همیشه `Evidence` دارد، نگاه کنید به
 * `Classifier.phishVerdict`). پس «`FalsePos`» یعنی طبقه‌بند پیامکی از این
 * چهار دسته را `Promo` یا `Phishing` بشناسد؛ هر چیز دیگری (از جمله `Unknown`)
 * طبق `ShowOnDoubt` در صندوق می‌ماند و خطای بی‌خطر است، نه `FalsePos`.
 *
 * «`FalsePos` برای `Phish`» شرط جدایی است و شامل همهٔ نمونه‌هایی می‌شود که
 * برچسبشان `Phishing` نیست — از جمله `Promo` — چون هیچ پیامک غیرفیشینگی
 * نباید فیشینگ اعلام شود، حتی اگر تبلیغ باشد.
 */
class GoldenSetTest {

    private val classifier = Classifier()

    private fun classify(example: GoldenExample) =
        classifier.classify(MessageInput(example.address, example.body, example.isKnownContact))

    @Test
    fun `no safe message is auto-hidden as promo or phishing`() {
        val safeCategories = setOf(Category.PERSONAL, Category.OTP, Category.BANK, Category.SERVICE)
        val hidingCategories = setOf(Category.PROMO, Category.PHISHING)

        val falsePositives = GoldenSet.all
            .filter { it.expectedCategory in safeCategories }
            .mapNotNull { example ->
                val actual = classify(example).category
                if (actual in hidingCategories) Triple(example, actual, example.expectedCategory) else null
            }

        assertTrue(
            "این پیامک‌های امن (Personal/OTP/Bank/Service) نباید Promo یا Phishing شمرده شوند " +
                "(FalsePos باید ۰ باشد)، ولی پنهان شدند:\n" +
                falsePositives.joinToString("\n") { (example, actual, expected) ->
                    "  [${example.address}] «${example.body.take(60)}» انتظار=$expected واقعی=$actual"
                },
            falsePositives.isEmpty(),
        )
    }

    @Test
    fun `promo recall is above 85 percent`() {
        val promoExamples = GoldenSet.all.filter { it.expectedCategory == Category.PROMO }
        check(promoExamples.isNotEmpty()) { "GoldenSet باید نمونهٔ Promo داشته باشد" }

        val missed = promoExamples.filter { classify(it).category != Category.PROMO }
        val recall = (promoExamples.size - missed.size).toDouble() / promoExamples.size

        assertTrue(
            "بازیابی Promo باید بیش از ۸۵٪ باشد؛ الان ${"%.1f".format(recall * 100)}٪ " +
                "(${promoExamples.size - missed.size} از ${promoExamples.size} شناخته شد).\n" +
                "نمونه‌های ازدست‌رفته:\n" +
                missed.joinToString("\n") { "  [${it.address}] «${it.body.take(60)}» واقعی=${classify(it).category}" },
            recall > 0.85,
        )
    }

    @Test
    fun `no non-phishing message is ever classified as phishing`() {
        val falsePositives = GoldenSet.all
            .filter { it.expectedCategory != Category.PHISHING }
            .mapNotNull { example ->
                val verdict = classify(example)
                if (verdict.category == Category.PHISHING) example to verdict else null
            }

        assertTrue(
            "هیچ پیامک غیرفیشینگی نباید Phishing شمرده شود (FalsePos باید ۰ باشد)، ولی این‌ها شدند:\n" +
                falsePositives.joinToString("\n") { (example, verdict) ->
                    "  [${example.address}] «${example.body.take(60)}» انتظار=${example.expectedCategory} " +
                        "واقعی=Phishing evidence=${verdict.evidence}"
                },
            falsePositives.isEmpty(),
        )
    }

    /**
     * زمان طبقه‌بندی هر پیامک زیر ۵ میلی‌ثانیه روی JVM (D35). معیار میانگین
     * روی یک اجرای گرم است، مثل الگوی موجود در `ClassifierTest`
     * («classifying one message stays well under five milliseconds»): بعد از
     * گرم شدن JIT، میانگین برای یک مسیر بحرانی مثل طبقه‌بندی پیامک نمایندهٔ
     * واقع‌بینانه‌تری از عملکرد است تا یک نمونهٔ حداکثری تک‌باره که ممکن است
     * فقط مکث GC را نشان دهد.
     */
    @Test
    fun `classifying a golden set message stays well under five milliseconds`() {
        val samples = GoldenSet.all
        val warmupRounds = 50
        val measuredRounds = 200

        repeat(warmupRounds) { samples.forEach { classify(it) } }

        val started = System.nanoTime()
        repeat(measuredRounds) { samples.forEach { classify(it) } }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
        val perMessageMillis = elapsedMillis / (measuredRounds * samples.size)

        assertTrue(
            "زمان طبقه‌بندی هر پیامک باید کمتر از ۵ میلی‌ثانیه باشد؛ الان $perMessageMillis میلی‌ثانیه " +
                "(میانگین روی ${measuredRounds * samples.size} طبقه‌بندی، پس از $warmupRounds دور گرم‌کردن)",
            perMessageMillis < 5.0,
        )
    }
}
