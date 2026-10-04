package ir.asudehapp.sms.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.model.Category

/** بند‌انگشتی‌های مخاطب، رمزگشایی‌شده یک بار و نگه‌داشته در حافظه تا اسکرول دوباره سراغشان نرود. */
private val avatarBitmapCache = LruCache<Uri, Bitmap>(60)

private val avatarPalette = listOf(
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB), Color(0xFF4FC3F7),
    Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFA1887F),
)

private fun avatarColor(address: String): Color {
    val hash = address.hashCode()
    val index = (if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)) % avatarPalette.size
    return avatarPalette[index]
}

private fun initialOf(text: String): String {
    val letter = text.trim().firstOrNull { it.isLetterOrDigit() } ?: return "؟"
    return letter.uppercaseChar().toString()
}

/**
 * ساختمان بانک، به سبک آیکون‌های Material. مجموعهٔ کامل آیکون‌ها (extended) را
 * فقط برای همین یکی به اپ نمی‌آوریم (بودجهٔ حجم APK).
 */
private val BankIcon: ImageVector by lazy {
    ImageVector.Builder("Bank", ICON_SIZE.dp, ICON_SIZE.dp, ICON_SIZE, ICON_SIZE)
        .addPath(
            addPathNodes("M4,10h3v7H4z M10.5,10h3v7h-3z M2,22h20v-3H2z M17,10v7h3v-7z M12,1L2,6v2h20V6z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}

private const val ICON_SIZE = 24f

/** آیکون دستهٔ یک فرستندهٔ بی‌مخاطب؛ برای بقیهٔ دسته‌ها همان حرف اول می‌ماند. */
private fun categoryIcon(category: Category): ImageVector? = when (category) {
    Category.BANK -> BankIcon
    Category.OTP -> Icons.Default.Lock
    Category.SERVICE -> Icons.Default.Settings
    else -> null
}

/**
 * آواتار یک گفتگو در فهرست: عکس مخاطب اگر مجوز مخاطب‌ها داده شده و مخاطب عکس
 * دارد، وگرنه دایرهٔ رنگی با حرف اول نام (یا شماره). رنگ از هش سرشماره ساخته
 * می‌شود، پس هر مخاطب همیشه همان رنگ را دارد. فرستندهٔ بی‌مخاطب (سرشمارهٔ
 * بانک، رمز یکبار مصرف، خدمات) به‌جای حرف اول آیکون دسته‌اش را می‌گیرد (D7).
 */
@Composable
fun ThreadAvatar(
    address: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    category: Category = Category.UNKNOWN,
) {
    val photoUri = LocalContactPhotos.current[address]
    val contactName = LocalContactNames.current[address]
    val name = contactName ?: address
    val icon = if (contactName == null) categoryIcon(category) else null
    val context = LocalContext.current
    // لینت این را نادرست «هیچ‌وقت value را عوض نمی‌کند» می‌داند، چون آرگومان‌های
    // نام‌دار الگوی مورد انتظارش را گم می‌کند؛ همان خط بعد value را عوض می‌کند.
    @Suppress("ProduceStateDoesNotAssignValue")
    val bitmap by produceState<Bitmap?>(initialValue = photoUri?.let(avatarBitmapCache::get), key1 = photoUri) {
        value = photoUri?.let { uri ->
            avatarBitmapCache.get(uri) ?: runCatching {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }.getOrNull()?.also { avatarBitmapCache.put(uri, it) }
        }
    }
    val loaded = bitmap
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (loaded == null) avatarColor(address) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            Image(
                loaded.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(size / 2), tint = Color.White)
        } else {
            Text(initialOf(name), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
