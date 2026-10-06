package ir.asudehapp.sms.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MmsPartInfo

/**
 * گالری پیوست‌های یک گفتگو (PARITY §ب-۶، ROADMAP E9): همهٔ تصویرها و فایل‌های
 * MMSهای همین گفتگو، تازه‌ترین اول. چیزی کپی یا جابه‌جا نمی‌شود؛ partها همان‌جا
 * در provider خوانده می‌شوند، مثل حباب پیامک.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentGallery(model: AsudehViewModel, messages: List<MessageEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var parts by remember { mutableStateOf<List<MmsPartInfo>?>(null) }
    LaunchedEffect(messages) {
        parts = messages
            .filter { it.kind == MessageEntity.KIND_MMS && it.attachments > 0 && !it.pendingDownload }
            .sortedByDescending { it.dateReceived }
            .flatMap { message -> model.mmsParts(message).filter { it.isAttachment } }
    }
    var viewing by remember { mutableStateOf<MmsPartInfo?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.attachments_gallery)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            val found = parts
            when {
                found == null -> Unit
                found.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.attachments_empty))
                }
                else -> GalleryGrid(model, found, Modifier.padding(padding)) { part ->
                    if (part.isImage) viewing = part else openExternally(context, model.partUri(part.partId), part.contentType)
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
private fun GalleryGrid(model: AsudehViewModel, parts: List<MmsPartInfo>, modifier: Modifier, onOpen: (MmsPartInfo) -> Unit) {
    val images = parts.filter { it.isImage }
    val files = parts.filterNot { it.isImage }
    LazyVerticalGrid(GridCells.Adaptive(GALLERY_CELL.dp), modifier.fillMaxSize()) {
        items(images, key = { it.partId }) { part ->
            Thumbnail(model, part) { onOpen(part) }
        }
        items(files, key = { it.partId }, span = { GridItemSpan(maxLineSpan) }) { part ->
            TextButton(onClick = { onOpen(part) }, Modifier.fillMaxWidth()) {
                Text("📎 " + (part.name ?: stringResource(R.string.open_attachment)))
            }
        }
    }
}

@Composable
private fun Thumbnail(model: AsudehViewModel, part: MmsPartInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(part.partId) { mutableStateOf(MmsBitmaps.cached(part.partId)) }
    LaunchedEffect(part.partId) {
        if (bitmap == null) bitmap = MmsBitmaps.load(context, model.partUri(part.partId), part.partId, THUMBNAIL_SIZE)
    }
    val cell = Modifier
        .padding(2.dp)
        .aspectRatio(1f)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClickLabel = stringResource(R.string.snippet_image), onClick = onClick)
    val image = bitmap
    if (image == null) {
        Box(cell)
    } else {
        Image(image, stringResource(R.string.snippet_image), cell, contentScale = ContentScale.Crop)
    }
}

/** کمترین پهنای هر خانهٔ گالری؛ گوشی پهن‌تر ستون بیشتری می‌گیرد. */
private const val GALLERY_CELL = 112
