package ir.asudehapp.sms.ui

import androidx.compose.ui.graphics.Color

/**
 * رنگ حباب پیامک‌های فرستاده، از یک پالت ثابت داخل اپ (ROADMAP E8). فروشگاه تم
 * نیست (مانیفست ۵-۵): فقط همین چند رنگ، که هرکدام نسخهٔ روشن و تیرهٔ خودش را
 * دارد تا متن پیامک روی آن خوانا بماند.
 *
 * شمارهٔ رنگ ذخیره می‌شود، نه خود رنگ؛ منفی یعنی «رنگ پوسته».
 */
object BubblePalette {

    class Swatch(val light: Color, val dark: Color)

    @Suppress("MagicNumber")
    val swatches: List<Swatch> = listOf(
        Swatch(Color(0xFFD3E4FF), Color(0xFF1F3A5F)),
        Swatch(Color(0xFFE8DDFF), Color(0xFF3B2F5C)),
        Swatch(Color(0xFFFFD9E2), Color(0xFF5C2B3A)),
        Swatch(Color(0xFFFFDDB8), Color(0xFF5A3A12)),
        Swatch(Color(0xFFF5E7A1), Color(0xFF4D4415)),
        Swatch(Color(0xFFCDEFC4), Color(0xFF234A22)),
        Swatch(Color(0xFFE2E2E2), Color(0xFF3A3A3A)),
    )

    /** رنگ شمارهٔ [index]، یا `null` برای رنگ پوسته (یا شماره‌ای که دیگر نیست). */
    fun color(index: Int, dark: Boolean): Color? =
        swatches.getOrNull(index)?.let { if (dark) it.dark else it.light }

    /** رنگ گفتگو بر رنگ سراسری مقدم است؛ هر دو منفی یعنی رنگ پوسته. */
    fun resolve(thread: Int, global: Int): Int = if (thread >= 0) thread else global
}
