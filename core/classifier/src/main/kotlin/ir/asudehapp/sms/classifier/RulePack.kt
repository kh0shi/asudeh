package ir.asudehapp.sms.classifier

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
    /** طعمه‌های کلاهبرداری: «برنده شدید»، «جایزه»… بدون `Evidence` فقط `Suspect` می‌سازند. */
    val scamBait: PatternGroup = PatternGroup(),
    val amount: PatternGroup = PatternGroup(),
    val brands: List<Brand> = emptyList(),
    val urlShorteners: List<String> = emptyList(),
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
    /** پیشوند سرشماره‌های رسمی. */
    val senders: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    /** عبارت‌هایی که یعنی پیامک خود را از این نهاد معرفی کرده است. */
    val mentions: List<String> = emptyList(),
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
    val scamBait: CompiledGroup = CompiledGroup(pack.scamBait)
    val amount: CompiledGroup = CompiledGroup(pack.amount)

    val brands: List<CompiledBrand> = pack.brands.map(::CompiledBrand)
    val shorteners: Set<String> = pack.urlShorteners.mapTo(mutableSetOf()) { it.lowercase() }
    val bayes: BayesModel = pack.bayes

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

    /** تعداد الگوهای متمایزی که در متن نرمال‌شده دیده شده‌اند. */
    fun hits(normalizedText: String): Int {
        var count = 0
        for (keyword in keywords) if (normalizedText.contains(keyword)) count++
        for (regex in regexes) if (regex.containsMatchIn(normalizedText)) count++
        return count
    }

    /** اولین الگوی دیده‌شده، برای ساختن `Reason`. */
    fun firstHit(normalizedText: String): String? {
        for (keyword in keywords) if (normalizedText.contains(keyword)) return keyword
        for (regex in regexes) regex.find(normalizedText)?.let { return it.value }
        return null
    }

    fun matches(normalizedText: String): Boolean = hits(normalizedText) > 0
}

class CompiledBrand(val brand: Brand) {
    val mentions: List<String> = brand.mentions.map { PersianText.normalize(it) }.filter { it.isNotEmpty() }
    val domains: List<String> = brand.domains.map { it.lowercase().removePrefix("www.") }
    val senders: List<String> = brand.senders.map { it.filter(Char::isDigit) }.filter { it.isNotEmpty() }

    fun isMentionedIn(normalizedText: String): Boolean = mentions.any { normalizedText.contains(it) }

    fun isOfficialSender(normalizedAddress: String): Boolean =
        senders.any { normalizedAddress.startsWith(it) }
}
