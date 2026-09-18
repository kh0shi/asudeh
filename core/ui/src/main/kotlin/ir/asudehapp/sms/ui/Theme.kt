package ir.asudehapp.sms.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * رنگ برند آسوده پیش‌فرض است و «رنگ پویای اندروید» یک گزینهٔ اختیاری (D49).
 *
 * فونت وزیرمتن (D51) هنوز داخل اپ قرار نگرفته است؛ فعلاً فونت پیش‌فرض سیستم
 * استفاده می‌شود.
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

    MaterialTheme(colorScheme = colorScheme, content = content)
}
