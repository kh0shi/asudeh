package ir.asudehapp.sms.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MmsPartInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * محتوای یک MMS در حباب پیام (D26): تصویرها، پیوست‌های دیگر، و دکمهٔ
 * «دریافت» برای پیامی که فقط اعلانش رسیده است. متن پیام جدا زیر همین نشان
 * داده می‌شود.
 */
@Composable
fun MmsContent(model: AsudehViewModel, message: MessageEntity) {
    if (message.pendingDownload) {
        Column {
            Text(stringResource(R.string.mms_pending), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { model.downloadMms(message) }) {
                Text(stringResource(R.string.mms_download))
            }
        }
        return
    }
    var parts by remember(message.providerId) { mutableStateOf<List<MmsPartInfo>>(emptyList()) }
    LaunchedEffect(message.providerId, message.attachments) { parts = model.mmsParts(message) }
    var viewing by remember { mutableStateOf<MmsPartInfo?>(null) }
    val context = LocalContext.current
    Column {
        for (part in parts.filter { it.isAttachment }) {
            if (part.isImage) {
                MmsImage(model.partUri(part.partId), part.partId) { viewing = part }
            } else {
                TextButton(onClick = { openExternally(context, model.partUri(part.partId), part.contentType) }) {
                    Text("📎 " + (part.name ?: stringResource(R.string.open_attachment)))
                }
            }
        }
    }
    viewing?.let { part ->
        ImageViewer(
            uri = model.partUri(part.partId),
            partId = part.partId,
            onOpenExternally = { openExternally(context, model.partUri(part.partId), part.contentType) },
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun MmsImage(uri: Uri, partId: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(partId) { mutableStateOf(MmsBitmaps.cached(partId)) }
    LaunchedEffect(partId) {
        if (bitmap == null) bitmap = MmsBitmaps.load(context, uri, partId, THUMBNAIL_SIZE)
    }
    val image = bitmap
    if (image == null) {
        Box(
            Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
    } else {
        Image(
            image,
            contentDescription = stringResource(R.string.snippet_image),
            modifier = Modifier
                .widthIn(max = 240.dp)
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.Fit,
        )
    }
}

/** نمایش تمام‌صفحهٔ تصویر، داخل خود اپ. */
@Composable
private fun ImageViewer(uri: Uri, partId: Long, onOpenExternally: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(partId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(partId) { bitmap = MmsBitmaps.load(context, uri, -partId, FULL_SIZE) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            TextButton(onClick = onOpenExternally, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                Text(stringResource(R.string.open_attachment), color = Color.White)
            }
        }
    }
}

/**
 * باز کردن پیوست با اپ دیگر. provider پیامک اجازهٔ موقت خواندن partها را
 * (`/part/`) به اپ مقصد می‌دهد؛ چیزی کپی نمی‌شود.
 */
private fun openExternally(context: Context, uri: Uri, contentType: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, contentType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_for_attachment, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.no_app_for_attachment, Toast.LENGTH_SHORT).show()
    }
}

/** تصویرهای MMS، کوچک‌شده و در حافظه نگه داشته‌شده تا اسکرول روان بماند. */
private object MmsBitmaps {

    private val cache = object : LruCache<Long, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(key: Long): ImageBitmap? = cache.get(key)

    suspend fun load(context: Context, uri: Uri, key: Long, maxSize: Int): ImageBitmap? =
        cache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }

    private const val CACHE_BYTES = 24 * 1024 * 1024
}

private const val THUMBNAIL_SIZE = 720
private const val FULL_SIZE = 2048
