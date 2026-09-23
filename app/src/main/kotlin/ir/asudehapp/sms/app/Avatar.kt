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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * آواتار یک گفتگو در فهرست: عکس مخاطب اگر مجوز مخاطب‌ها داده شده و مخاطب عکس
 * دارد، وگرنه دایرهٔ رنگی با حرف اول نام (یا شماره). رنگ از هش سرشماره ساخته
 * می‌شود، پس هر مخاطب همیشه همان رنگ را دارد.
 */
@Composable
fun ThreadAvatar(address: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val photoUri = LocalContactPhotos.current[address]
    val name = LocalContactNames.current[address] ?: address
    val context = LocalContext.current
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
        } else {
            Text(initialOf(name), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
