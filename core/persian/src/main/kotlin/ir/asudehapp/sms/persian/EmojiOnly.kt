package ir.asudehapp.sms.persian

/**
 * پیامکی که فقط ۱ تا ۳ ایموجی است، بزرگ و بی‌حباب نشان داده می‌شود (ROADMAP D7).
 *
 * ایموجی‌ها اینجا «خوشه» شمرده می‌شوند، نه نویسه: 👍🏽 (با رنگ پوست)، 👨‍👩‍👧
 * (چند ایموجی با ZWJ)، 🇮🇷 (دو نشانهٔ منطقه) و 1️⃣ (keycap) هرکدام یکی‌اند.
 * فاصله و خط تازه نادیده گرفته می‌شوند. هر نویسهٔ دیگری، حتی یک حرف یا عدد تنها،
 * یعنی پیامک معمولی است.
 */
object EmojiOnly {

    const val MAX: Int = 3

    /** شمار ایموجی‌های [text] اگر فقط ۱ تا [MAX] ایموجی باشد، وگرنه `null`. */
    fun count(text: String): Int? {
        val points = text.codePoints().toArray().filterNot { Character.isWhitespace(it) }
        var clusters = 0
        var index = 0
        while (index in points.indices && clusters <= MAX) {
            clusters++
            // نویسهٔ غیرایموجی: حلقه تمام می‌شود و پایین `null` برمی‌گردد.
            index = clusterEnd(points, index) ?: Int.MAX_VALUE
        }
        return clusters.takeIf { index == points.size && it in 1..MAX }
    }

    /** پایان خوشه‌ای که از [start] شروع می‌شود، یا `null` اگر آنجا ایموجی نیست. */
    private fun clusterEnd(points: List<Int>, start: Int): Int? {
        val first = points[start]
        return when {
            // پرچم: دقیقاً دو نشانهٔ منطقه.
            first in REGIONAL -> (start + 2).takeIf { points.getOrNull(start + 1) in REGIONAL }
            first.isKeycapBase() -> keycapEnd(points, start + 1)
            first.isEmojiBase() -> sequenceEnd(points, start + 1)
            else -> null
        }
    }

    /** `1️⃣`: رقم یا `#` یا `*`، شاید U+FE0F، و حتماً U+20E3. */
    private fun keycapEnd(points: List<Int>, afterBase: Int): Int? {
        val keycap = if (points.getOrNull(afterBase) == VARIATION) afterBase + 1 else afterBase
        return (keycap + 1).takeIf { points.getOrNull(keycap) == KEYCAP }
    }

    /** رنگ پوست، U+FE0F، برچسب‌ها، و ایموجی‌های بعدی که با ZWJ چسبیده‌اند. */
    private fun sequenceEnd(points: List<Int>, afterBase: Int): Int {
        var index = afterBase
        var extending = true
        while (extending && index < points.size) {
            val point = points[index]
            when {
                point == VARIATION || point in SKIN_TONES || point in TAGS -> index++
                point == ZWJ && points.getOrNull(index + 1)?.isEmojiBase() == true -> index += 2
                else -> extending = false
            }
        }
        return index
    }

    private fun Int.isEmojiBase(): Boolean = EMOJI_RANGES.any { this in it }

    private fun Int.isKeycapBase(): Boolean = this in '0'.code..'9'.code || this == '#'.code || this == '*'.code

    private const val ZWJ = 0x200D
    private const val VARIATION = 0xFE0F
    private const val KEYCAP = 0x20E3

    @Suppress("MagicNumber")
    private val REGIONAL = 0x1F1E6..0x1F1FF

    @Suppress("MagicNumber")
    private val SKIN_TONES = 0x1F3FB..0x1F3FF

    @Suppress("MagicNumber")
    private val TAGS = 0xE0020..0xE007F

    /** نویسه‌هایی که خودشان ایموجی‌اند (یا با U+FE0F ایموجی نشان داده می‌شوند). */
    @Suppress("MagicNumber")
    private val EMOJI_RANGES = listOf(
        0x1F000..0x1FAFF,
        0x2600..0x27BF,
        0x2300..0x23FF,
        0x2B00..0x2BFF,
        0x2190..0x21FF,
        0x00A9..0x00A9,
        0x00AE..0x00AE,
        0x203C..0x203C,
        0x2049..0x2049,
        0x2122..0x2122,
        0x2139..0x2139,
        0x3030..0x3030,
        0x303D..0x303D,
        0x3297..0x3297,
        0x3299..0x3299,
    )
}
