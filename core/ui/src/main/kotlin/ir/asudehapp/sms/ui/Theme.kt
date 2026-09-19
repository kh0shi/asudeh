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
import androidx.compose.ui.text.style.TextDirection
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

/*
 * همهٔ نقش‌های رنگی از رنگ برند ساخته می‌شوند؛ وگرنه Material 3 برای نقش‌هایی
 * که تعریف نشده‌اند (مثل حباب پیام ارسالی یا دکمهٔ «گفتگوی تازه») رنگ بنفش
 * پیش‌فرض خودش را می‌گذارد.
 */
private val LightScheme = lightColorScheme(
    primary = AsudehColors.Calm,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EBDC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4F6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8E2),
    onSecondaryContainer = Color(0xFF0C1F1C),
    tertiary = Color(0xFF7A5A2E),
    tertiaryContainer = Color(0xFFFFDDB3),
    background = AsudehColors.Sand,
    onBackground = Color(0xFF1A1C1A),
    surface = AsudehColors.Sand,
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE3E6DF),
    onSurfaceVariant = Color(0xFF424844),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F4EE),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE7E0),
    surfaceContainerHighest = Color(0xFFE4E1DA),
    outline = Color(0xFF727873),
    outlineVariant = Color(0xFFC2C8C2),
)

private val DarkScheme = darkColorScheme(
    primary = AsudehColors.CalmDark,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF1F5146),
    onPrimaryContainer = Color(0xFFB8EBDC),
    secondary = Color(0xFFB6CCC7),
    onSecondary = Color(0xFF213531),
    secondaryContainer = Color(0xFF374B47),
    onSecondaryContainer = Color(0xFFD2E8E2),
    tertiary = Color(0xFFEBC08C),
    tertiaryContainer = Color(0xFF5F4318),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF12140F),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0D0F0B),
    surfaceContainerLow = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF1E201D),
    surfaceContainerHigh = Color(0xFF282A27),
    surfaceContainerHighest = Color(0xFF333532),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
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

/**
 * جهت هر متن از اولین حرف قوی خودش می‌آید (D50)، نه از جهت رابط. پیش‌فرض
 * Compose جهت رابط است؛ آن‌وقت متن انگلیسی پیامک راست‌به‌چپ چیده می‌شد، و در
 * پنجره‌های popup که جهت گوشی را می‌گیرند، متن فارسی چپ‌به‌راست.
 */
private fun TextStyle.inVazirmatn(): TextStyle = copy(fontFamily = Vazirmatn, textDirection = TextDirection.Content)

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
