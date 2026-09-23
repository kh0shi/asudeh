package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.persian.PersianText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * قواعد و مدل طبقه‌بند، به‌صورت یک فایل دادهٔ نسخه‌دار (D33).
 *
 * فایل کنار همین ماژول در `resources` قرار دارد تا هم آزمون‌های JVM و هم اپ
 * اندرویدی از یک نسخه بخوانند. انتشار ماهانهٔ فقط-داده فقط این فایل را عوض می‌کند.
 */
@Serializable
data class RulePack(
    /** نسخهٔ `RulePack` بر اساس تاریخ است، جدا از نسخهٔ اپ (D66). */
    val version: String,
    val adLinePrefixes: List<String> = listOf("1000", "2000", "3000", "5000", "9000"),
    val otp: PatternGroup = PatternGroup(),
    val bank: PatternGroup = PatternGroup(),
    val service: PatternGroup = PatternGroup(),
    val promo: PatternGroup = PatternGroup(),
    /** نشانه‌های قوی تبلیغ؛ یکی از این‌ها به‌تنهایی وزن دو نشانهٔ عادی را دارد. */
    val promoStrong: PatternGroup = PatternGroup(),
    /**
     * نشانه‌های قطعی تراکنش بانکی («برداشت»، «واریز»…). پیامکی که نشانهٔ قوی
     * تبلیغ دارد فقط با یکی از این‌ها بانکی شمرده می‌شود.
     */
    val bankTransaction: PatternGroup = PatternGroup(),
    /** طعمه‌های کلاهبرداری: «برنده شدید»، «جایزه»… بدون `Evidence` فقط `Suspect` می‌سازند. */
    val scamBait: PatternGroup = PatternGroup(),
    /**
     * واژه‌هایی که یعنی عددِ پس از «کد» رمز یکبار مصرف نیست: «کد تخفیف»،
     * «کد پیگیری»، «کد ملی». فقط `keywords` آن به کار می‌رود ([OneTimeCode]).
     */
    val codeNotOtp: PatternGroup = PatternGroup(),
    val amount: PatternGroup = PatternGroup(),
    val brands: List<Brand> = emptyList(),
    /**
     * دامنه‌هایی که می‌شناسیمشان. لینکِ این‌ها هیچ‌وقت مشکوک نیست، و لینکی که
     * نه اینجاست و نه دامنهٔ رسمی یک `Brand`، از فرستندهٔ ناشناس هشدار می‌گیرد.
     * هر ورودی خودِ دامنه و زیردامنه‌هایش را می‌گیرد؛ `maps.app.goo.gl` را
     * می‌شناسیم ولی `goo.gl` را نه، چون کوتاه‌کننده مقصدش را پنهان می‌کند.
     */
    val knownHosts: List<String> = emptyList(),
    /**
     * سرشماره‌های حرفیِ صرفاً تبلیغاتی. مثل `AdLine` رفتار می‌کنند: پیامکشان با
     * کمترین نشانهٔ تبلیغ، تبلیغِ قطعی است.
     */
    val promoSenders: List<String> = emptyList(),
    /**
     * سرشماره‌های شناخته‌شده‌ای که در هیچ `Brand` نیستند. لینکِ این‌ها به‌تنهایی
     * هشدار نمی‌گیرد؛ خودِ لینکِ جعلی (punycode، نویسهٔ شبیه‌هم) می‌گیرد.
     */
    val trustedSenders: List<String> = emptyList(),
    val bayes: BayesModel = BayesModel(),
) {
    companion object {
        private const val RESOURCE = "/ir/asudehapp/sms/classifier/rulepack.json"

        private val json = Json {
            ignoreUnknownKeys = false
            prettyPrint = false
        }

        fun parse(text: String): RulePack = json.decodeFromString(serializer(), text)

        /** `RulePack` همراه اپ. */
        fun bundled(): RulePack {
            val stream = RulePack::class.java.getResourceAsStream(RESOURCE)
                ?: error("فایل قواعد پیدا نشد: $RESOURCE")
            return parse(stream.bufferedReader().use { it.readText() })
        }
    }
}

@Serializable
data class PatternGroup(
    /** روی متن نرمال‌شده جستجو می‌شوند (D50). */
    val keywords: List<String> = emptyList(),
    val regexes: List<String> = emptyList(),
)

/** نهادی که ممکن است جعل شود، با سرشماره‌ها و دامنه‌های رسمی‌اش (اصل ۴). */
@Serializable
data class Brand(
    val name: String,
    /** پیشوند سرشماره‌های عددی رسمی. */
    val senders: List<String> = emptyList(),
    /**
     * تکه‌ای از سرشماره‌های حرفی رسمی. اپراتورها از ده‌ها نام می‌فرستند
     * (`Irancell`، `MissedCalls`، `SoratHesab`…) و `irancell` همهٔ `IrancellMKP`
     * و `.IRANCELL.` را هم می‌گیرد.
     */
    val senderIds: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    /** عبارت‌هایی که یعنی پیامک خود را از این نهاد معرفی کرده است. */
    val mentions: List<String> = emptyList(),
    /**
     * آیا جعل این نهاد آن‌قدر پرخطر است که «سرشمارهٔ غیررسمی» به‌تنهایی شاهد
     * شمرده شود؟ فقط بانک و نهاد دولتی. نام بردن از اپراتور یا پست در متن یک
     * پیامک عادی است (فروشگاهی که می‌نویسد «به حساب بانک صادرات واریز کنید»)،
     * پس برای آن‌ها فقط جعل دامنه شاهد است (اصل ۴).
     */
    val sensitive: Boolean = false,
)

/** مدل Bayes از پیش آموزش‌دیده. روی گوشی آموزشی انجام نمی‌شود (D32 ب). */
@Serializable
data class BayesModel(
    val bias: Double = 0.0,
    val weights: Map<String, Double> = emptyMap(),
    val threshold: Double = 2.0,
)

/** `RulePack` با الگوهای از پیش کامپایل‌شده، تا طبقه‌بندی هر پیامک زیر ۵ میلی‌ثانیه بماند (D35). */
class CompiledRulePack(val pack: RulePack) {

    val otp: CompiledGroup = CompiledGroup(pack.otp)
    val bank: CompiledGroup = CompiledGroup(pack.bank)
    val service: CompiledGroup = CompiledGroup(pack.service)
    val promo: CompiledGroup = CompiledGroup(pack.promo)
    val promoStrong: CompiledGroup = CompiledGroup(pack.promoStrong)
    val bankTransaction: CompiledGroup = CompiledGroup(pack.bankTransaction)
    val scamBait: CompiledGroup = CompiledGroup(pack.scamBait)
    val amount: CompiledGroup = CompiledGroup(pack.amount)

    /** واژه‌های «این کد رمز نیست»، نرمال‌شده، برای [OneTimeCode]. */
    val codeNotOtp: List<String> =
        pack.codeNotOtp.keywords.map { PersianText.normalize(it) }.filter { it.isNotEmpty() }

    val brands: List<CompiledBrand> = pack.brands.map(::CompiledBrand)
    val bayes: BayesModel = pack.bayes

    private val knownHosts: List<String> = pack.knownHosts.map { it.lowercase().removePrefix("www.") }

    private val promoSenders: List<String> = pack.promoSenders.map { Addresses.normalize(it) }.filter { it.isNotEmpty() }
    private val trustedSenders: List<String> =
        pack.trustedSenders.map { Addresses.normalize(it) }.filter { it.isNotEmpty() }

    /** سرشمارهٔ حرفیِ صرفاً تبلیغاتی، مثل `BaIrancell`. */
    fun isPromoSender(normalizedAddress: String): Boolean =
        promoSenders.any { normalizedAddress.contains(it) }

    /** سرشمارهٔ شناخته‌شده: یا سرشمارهٔ رسمی یک `Brand` است یا در فهرست شناخته‌شده‌ها. */
    fun isTrustedSender(normalizedAddress: String): Boolean =
        trustedSenders.any { normalizedAddress.contains(it) } || brands.any { it.isOfficialSender(normalizedAddress) }

    /** میزبانی که می‌شناسیمش: خودِ دامنه یا زیردامنهٔ آن، در فهرست یا در `Brand`ها. */
    fun isKnownHost(host: String): Boolean {
        val clean = host.lowercase().removePrefix("www.")
        return knownHosts.any { clean == it || clean.endsWith(".$it") } || brands.any { it.isOfficialHost(clean) }
    }

    companion object {
        fun bundled(): CompiledRulePack = CompiledRulePack(RulePack.bundled())
    }
}

class CompiledGroup(group: PatternGroup) {

    /**
     * کلیدواژه‌ها روی متن نرمال‌شده مقایسه می‌شوند، و کلیدواژه‌ای که کلیدواژهٔ
     * کوتاه‌تری را در خود دارد کنار گذاشته می‌شود؛ وگرنه «کد تخفیف» و «تخفیف»
     * روی یک عبارت دو بار شمرده می‌شوند و امتیاز را بی‌جهت بالا می‌برند.
     */
    private val keywords: List<String> = group.keywords
        .map { PersianText.normalize(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .let { all -> all.filterNot { candidate -> all.any { it != candidate && candidate.contains(it) } } }
    private val regexes: List<Regex> = group.regexes.map { Regex(it, RegexOption.IGNORE_CASE) }

    /**
     * تعداد الگوهای متمایزی که در متن نرمال‌شده دیده شده‌اند. کلیدواژه فقط
     * به‌صورت کلمهٔ کامل شمرده می‌شود («چک» درون «کوچک» شمرده نمی‌شود).
     */
    fun hits(normalizedText: String): Int {
        var count = 0
        for (keyword in keywords) if (PersianText.containsWord(normalizedText, keyword)) count++
        for (regex in regexes) if (regex.containsMatchIn(normalizedText)) count++
        return count
    }

    /** اولین الگوی دیده‌شده، برای ساختن `Reason`. */
    fun firstHit(normalizedText: String): String? {
        for (keyword in keywords) if (PersianText.containsWord(normalizedText, keyword)) return keyword
        for (regex in regexes) regex.find(normalizedText)?.let { return it.value }
        return null
    }

    fun matches(normalizedText: String): Boolean = hits(normalizedText) > 0
}

class CompiledBrand(val brand: Brand) {
    val mentions: List<String> = brand.mentions.map { PersianText.normalize(it) }.filter { it.isNotEmpty() }
    val domains: List<String> = brand.domains.map { it.lowercase().removePrefix("www.") }
    val senders: List<String> = brand.senders.map { it.filter(Char::isDigit) }.filter { it.isNotEmpty() }
    val senderIds: List<String> = brand.senderIds.map { Addresses.normalize(it) }.filter { it.isNotEmpty() }

    /** جعل این نهاد پرخطر است و «سرشمارهٔ غیررسمی» برایش شاهد شمرده می‌شود. */
    val isSensitive: Boolean = brand.sensitive

    fun isMentionedIn(normalizedText: String): Boolean =
        mentions.any { PersianText.containsWord(normalizedText, it) }

    fun isOfficialSender(normalizedAddress: String): Boolean =
        senders.any { normalizedAddress.startsWith(it) } || senderIds.any { normalizedAddress.contains(it) }

    /** میزبان، خود یکی از دامنه‌های رسمی این نهاد یا زیردامنهٔ آن است. */
    fun isOfficialHost(host: String): Boolean {
        val clean = host.lowercase().removePrefix("www.")
        return domains.any { clean == it || clean.endsWith(".$it") }
    }
}
