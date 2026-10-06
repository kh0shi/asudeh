package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.model.QuickReplies

/**
 * «پاسخ‌های آماده» (ADR-0017): فقط متن‌هایی که خود کاربر می‌نویسد. فهرست در
 * اولین اجرا خالی است و اپ هیچ نمونه یا پیشنهادی جلوی کاربر نمی‌گذارد؛ حتی
 * کادر نوشتن مثال جمله‌ای ندارد.
 *
 * نوشتن، ویرایش (با لمس ردیف)، حذف، و جابه‌جایی با دکمه‌های بالا و پایین.
 */
@Composable
fun QuickRepliesScreen(model: AsudehViewModel) {
    val replies by model.quickReplies.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { QuickRepliesHeader(count = replies.size, onAdd = { adding = true }) }
        itemsIndexed(replies, key = { _, reply -> reply }) { index, reply ->
            QuickReplyRow(
                reply = reply,
                onEdit = { editing = reply },
                onMoveUp = if (index > 0) ({ model.moveQuickReply(reply, -1) }) else null,
                onMoveDown = if (index < replies.lastIndex) ({ model.moveQuickReply(reply, 1) }) else null,
                onDelete = { deleting = reply },
            )
            HorizontalDivider()
        }
    }

    if (adding) {
        QuickReplyDialog(
            replies = replies,
            original = null,
            onDismiss = { adding = false },
            onSave = {
                adding = false
                model.addQuickReply(it)
            },
        )
    }
    editing?.let { old ->
        QuickReplyDialog(
            replies = replies,
            original = old,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                model.editQuickReply(old, it)
            },
        )
    }
    deleting?.let { reply ->
        DeleteQuickReplyDialog(
            reply = reply,
            onDismiss = { deleting = null },
            onDelete = {
                deleting = null
                model.removeQuickReply(reply)
            },
        )
    }
}

/** «افزودن پاسخ»، توضیح مرز ADR-0017، و جملهٔ فهرست خالی (بدون هیچ متن نمونه). */
@Composable
private fun QuickRepliesHeader(count: Int, onAdd: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onAdd, enabled = count < QuickReplies.MAX_COUNT) {
                Text(stringResource(R.string.quick_reply_add))
            }
        }
        Text(
            stringResource(R.string.quick_replies_hint),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (count == 0) {
            EmptyState(stringResource(R.string.quick_replies_empty))
        }
    }
}

/** حذف متنی که خود کاربر نوشته برگشت‌پذیر نیست، پس یک بار پرسیده می‌شود. */
@Composable
private fun DeleteQuickReplyDialog(reply: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_reply_delete_title)) },
        text = { Text(reply, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** یک پاسخ؛ [onMoveUp] و [onMoveDown] در دو سر فهرست `null` و دکمه‌شان خاموش است. */
@Composable
private fun QuickReplyRow(
    reply: String,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            reply,
            Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.quick_reply_move_up))
        }
        IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.quick_reply_move_down))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete))
        }
    }
}

/** نوشتن یا ویرایش یک پاسخ. کادر خالی شروع می‌شود؛ هیچ متن نمونه‌ای ندارد (ADR-0017). */
@Composable
private fun QuickReplyDialog(
    replies: List<String>,
    original: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(original.orEmpty()) }
    val problem = remember(text, replies) { QuickReplies.problem(replies, text, replacing = original) }
    // کادر خالی خطا نیست؛ فقط «ذخیره» خاموش می‌ماند.
    val message: String? = when (problem) {
        QuickReplies.Problem.TOO_LONG ->
            Texts.digits(stringResource(R.string.quick_reply_too_long, QuickReplies.MAX_LENGTH))
        QuickReplies.Problem.DUPLICATE -> stringResource(R.string.quick_reply_duplicate)
        QuickReplies.Problem.FULL -> Texts.digits(stringResource(R.string.quick_reply_full, QuickReplies.MAX_COUNT))
        QuickReplies.Problem.BLANK, null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (original == null) R.string.quick_reply_new else R.string.quick_reply_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.quick_reply_text)) },
                    isError = message != null,
                    supportingText = if (message != null) {
                        { Text(message) }
                    } else {
                        null
                    },
                    minLines = 2,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = problem == null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
