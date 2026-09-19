package ir.asudehapp.sms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import ir.asudehapp.sms.model.Folder

/** یک تکه از گفتگو: یا یک پیامک دیده‌شده، یا نوار جمع‌شدهٔ `HiddenRun`. */
private sealed interface ConversationItem {
    data class Visible(val message: MessageEntity) : ConversationItem

    /**
     * پیامک‌های پنهانِ پشت‌سرهم **از یک پوشه**، سر جای زمانی خودشان، به یک نوار
     * جمع‌شده تبدیل می‌شوند (ADR-0005، D43). تبلیغ و کلاهبرداری هرگز در یک نوار
     * نیستند.
     */
    data class Hidden(val folder: Folder, val messages: List<MessageEntity>) : ConversationItem
}

@Composable
fun ConversationScreen(model: AsudehViewModel, isDefaultApp: Boolean) {
    val state by model.conversation.collectAsState()
    val draft by model.draft.collectAsState()
    val sendError by model.sendError.collectAsState()
    var expandedRuns by remember { mutableStateOf(setOf<Long>()) }
    var reasonFor by remember { mutableStateOf<MessageEntity?>(null) }

    val items = remember(state.messages, state.folder) {
        buildItems(state.messages, state.folder)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items.size) { position ->
                when (val item = items[position]) {
                    is ConversationItem.Visible -> MessageBubble(
                        message = item.message,
                        dimmed = false,
                        onWhy = { reasonFor = item.message },
                        onResend = { model.resend(item.message) },
                    )

                    is ConversationItem.Hidden -> {
                        val key = item.messages.first().providerId
                        val expanded = key in expandedRuns
                        HiddenRunBar(
                            folder = item.folder,
                            count = item.messages.size,
                            expanded = expanded,
                            onToggle = {
                                expandedRuns =
                                    if (expanded) expandedRuns - key else expandedRuns + key
                            },
                        )
                        if (expanded && item.folder == Folder.SCAM) {
                            Text(
                                stringResource(R.string.scam_rescue_hint),
                                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (expanded) {
                            for (hidden in item.messages) {
                                MessageBubble(
                                    message = hidden,
                                    dimmed = true,
                                    onWhy = { reasonFor = hidden },
                                    // پیامک مشکوک به کلاهبرداری با یک لمس برنمی‌گردد؛
                                    // اول باید دلیلش دیده شود (D44).
                                    onRescue = if (hidden.folder == Folder.PROMO) {
                                        { model.rescue(hidden) }
                                    } else {
                                        null
                                    },
                                    onResend = { model.resend(hidden) },
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Composer(
            draft = draft,
            enabled = isDefaultApp && state.address.isNotBlank(),
            isDefaultApp = isDefaultApp,
            onDraftChange = model::updateDraft,
            onSend = model::send,
        )
    }

    if (sendError) {
        AlertDialog(
            onDismissRequest = model::dismissSendError,
            text = { Text(stringResource(R.string.send_error)) },
            confirmButton = {
                TextButton(onClick = model::dismissSendError) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    reasonFor?.let { message ->
        ReasonSheet(
            message = message,
            isAdLine = state.isAdLine,
            onDismiss = { reasonFor = null },
            onRescue = {
                reasonFor = null
                model.rescue(message)
            },
            onBlock = {
                reasonFor = null
                model.block(message.address)
            },
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    enabled: Boolean,
    isDefaultApp: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isDefaultApp) {
            Text(
                stringResource(R.string.send_not_default),
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.compose_hint)) },
                maxLines = 5,
            )
            TextButton(onClick = onSend, enabled = enabled && draft.isNotBlank()) {
                Text(stringResource(R.string.send))
            }
        }
    }
}

/**
 * وقتی گفتگو از `Inbox` باز می‌شود، پیامک‌های پوشه‌های دیگر جمع می‌شوند. وقتی از
 * خود `PromoFolder` باز شود، فقط پیامک‌های همان پوشه دیده می‌شوند (ADR-0005).
 */
private fun buildItems(
    messages: List<MessageEntity>,
    folder: Folder,
): List<ConversationItem> {
    if (folder != Folder.INBOX) {
        return messages.filter { it.folder == folder }.map(ConversationItem::Visible)
    }
    val items = mutableListOf<ConversationItem>()
    val run = mutableListOf<MessageEntity>()
    fun flush() {
        if (run.isNotEmpty()) {
            items += ConversationItem.Hidden(run.first().folder, run.toList())
            run.clear()
        }
    }
    for (message in messages) {
        when {
            message.folder == Folder.INBOX -> {
                flush()
                items += ConversationItem.Visible(message)
            }
            run.isNotEmpty() && run.first().folder != message.folder -> {
                flush()
                run += message
            }
            else -> run += message
        }
    }
    flush()
    return items
}

@Composable
private fun HiddenRunBar(folder: Folder, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    // نوار یک‌خطی با رنگ خنثی (D43)؛ کلاهبرداری با رنگ هشدار.
    val scam = folder == Folder.SCAM
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(
                if (scam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Texts.count(if (scam) R.plurals.hidden_scam_run else R.plurals.hidden_promo_run, count),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    dimmed: Boolean,
    onWhy: () -> Unit,
    onResend: () -> Unit,
    onRescue: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
    ) {
        if (message.risk) {
            // نوار هشدار `Suspect` بالای پیامک (D46).
            Text(
                stringResource(R.string.suspect_warning),
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Box(
            Modifier
                .background(
                    if (message.outgoing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onWhy)
                .padding(12.dp),
        ) {
            // متن پیامک هرگز تغییر نمی‌کند: نه ارقامش و نه چیز دیگر (D50).
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dimmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (message.risk) {
            Text(stringResource(R.string.links_disabled), style = MaterialTheme.typography.labelSmall)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Texts.timestamp(message.dateReceived), style = MaterialTheme.typography.labelSmall)
            when (message.sendStatus) {
                // ارسال ناموفق هرگز بی‌صدا نیست.
                SendStatus.FAILED -> {
                    Text(
                        stringResource(R.string.send_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onResend) { Text(stringResource(R.string.resend)) }
                }
                SendStatus.PENDING -> Text(
                    stringResource(R.string.send_pending),
                    style = MaterialTheme.typography.labelSmall,
                )
                SendStatus.SENT, SendStatus.NONE -> Unit
            }
            if (onRescue != null) {
                TextButton(onClick = onRescue) { Text(stringResource(R.string.rescue)) }
            }
        }
    }
}

/** برگهٔ «چرا اینجاست؟» با دکمه‌های `Rescue` و `Block` (D44). */
@Composable
private fun ReasonSheet(
    message: MessageEntity,
    isAdLine: Boolean,
    onDismiss: () -> Unit,
    onRescue: () -> Unit,
    onBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.why_here)) },
        text = {
            Column {
                Text(Texts.reason(message))
                if (message.suggestMove) {
                    Text(
                        stringResource(R.string.reason_suggest_move),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "${Texts.folderName(message.folder)} · ${Texts.sender(message.address)}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (isAdLine) {
                    Text(stringResource(R.string.unsub), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            if (message.outgoing) {
                Unit
            } else if (message.folder != Folder.INBOX) {
                TextButton(onClick = onRescue) { Text(stringResource(R.string.rescue)) }
            } else {
                TextButton(onClick = onBlock) { Text(stringResource(R.string.block)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
