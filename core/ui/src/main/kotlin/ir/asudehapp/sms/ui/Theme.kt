package ir.asudehapp.sms.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection

/**
 * رنگ برند آسوده پیش‌فرض است و «رنگ پویای اندروید» یک گزینهٔ اختیاری (D49).
 */
object AsudehColors {
    val Calm = Color(0xFF2F6F62)
    val CalmDark = Color(0xFF9CD3C4)
    val Sand = Color(0xFFF3EFE7)
    val Warning = Color(0xFFB8860B)
    val WarningContainer = Color(0xFFFFF3CD)
}

private val LightScheme = lightColorScheme(
    primary = AsudehColors.Calm,
    onPrimary = Color.White,
    secondary = Color(0xFF4F6360),
    background = AsudehColors.Sand,
    surface = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = AsudehColors.CalmDark,
    onPrimary = Color(0xFF00382C),
    secondary = Color(0xFFB6CCC7),
    background = Color(0xFF12140F),
    surface = Color(0xFF1A1C19),
)

/**
 * وزیرمتن متغیر (D51)، برای فارسی و لاتین. یک فایل همهٔ وزن‌ها را دارد؛ هر وزنی
 * که تم به کار می‌برد اینجا با محور `wght` اعلام می‌شود.
 * مجوز: SIL Open Font License 1.1 (`core/ui/src/main/VAZIRMATN-OFL.txt`).
 */
@OptIn(ExperimentalTextApi::class)
val Vazirmatn: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.vazirmatn,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun TextStyle.inVazirmatn(): TextStyle = copy(fontFamily = Vazirmatn)

private val AsudehTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.inVazirmatn(),
        displayMedium = displayMedium.inVazirmatn(),
        displaySmall = displaySmall.inVazirmatn(),
        headlineLarge = headlineLarge.inVazirmatn(),
        headlineMedium = headlineMedium.inVazirmatn(),
        headlineSmall = headlineSmall.inVazirmatn(),
        titleLarge = titleLarge.inVazirmatn(),
        titleMedium = titleMedium.inVazirmatn(),
        titleSmall = titleSmall.inVazirmatn(),
        bodyLarge = bodyLarge.inVazirmatn(),
        bodyMedium = bodyMedium.inVazirmatn(),
        bodySmall = bodySmall.inVazirmatn(),
        labelLarge = labelLarge.inVazirmatn(),
        labelMedium = labelMedium.inVazirmatn(),
        labelSmall = labelSmall.inVazirmatn(),
    )
}

@Composable
fun AsudehTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    // جهت رابط از زبان رابط می‌آید، نه از زبان گوشی: روی گوشی انگلیسی هم رابط
    // فارسی راست‌به‌چپ است. جهت متن هر پیامک جدا از روی اولین حرف قوی‌اش
    // تعیین می‌شود (D50).
    val direction = if (booleanResource(R.bool.asudeh_rtl_ui)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(colorScheme = colorScheme, typography = AsudehTypography, content = content)
    }
}
