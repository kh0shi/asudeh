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
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder

/** یک تکه از گفتگو: یا یک پیامک دیده‌شده، یا نوار جمع‌شدهٔ `HiddenRun`. */
private sealed interface ConversationItem {
    data class Visible(val message: MessageEntity) : ConversationItem

    /**
     * پیامک‌های پنهانِ پشت‌سرهم، سر جای زمانی خودشان، به یک نوار جمع‌شده تبدیل
     * می‌شوند (ADR-0005، D43).
     */
    data class Hidden(val messages: List<MessageEntity>) : ConversationItem
}

@Composable
fun ConversationScreen(model: AsudehViewModel) {
    val state by model.conversation.collectAsState()
    var expandedRuns by remember { mutableStateOf(setOf<Long>()) }
    var reasonFor by remember { mutableStateOf<MessageEntity?>(null) }

    val items = remember(state.messages, state.folder) {
        buildItems(state.messages, state.folder)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items.size) { position ->
            when (val item = items[position]) {
                is ConversationItem.Visible -> MessageBubble(
                    message = item.message,
                    dimmed = false,
                    onWhy = { reasonFor = item.message },
                )

                is ConversationItem.Hidden -> {
                    val key = item.messages.first().providerId
                    val expanded = key in expandedRuns
                    HiddenRunBar(
                        count = item.messages.size,
                        expanded = expanded,
                        onToggle = {
                            expandedRuns =
                                if (expanded) expandedRuns - key else expandedRuns + key
                        },
                    )
                    if (expanded) {
                        for (hidden in item.messages) {
                            MessageBubble(
                                message = hidden,
                                dimmed = true,
                                onWhy = { reasonFor = hidden },
                                onRescue = { model.rescue(hidden) },
                            )
                        }
                    }
                }
            }
        }
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
            items += ConversationItem.Hidden(run.toList())
            run.clear()
        }
    }
    for (message in messages) {
        if (message.folder == Folder.INBOX) {
            flush()
            items += ConversationItem.Visible(message)
        } else {
            run += message
        }
    }
    flush()
    return items
}

@Composable
private fun HiddenRunBar(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    // نوار یک‌خطی با رنگ خنثی (D43).
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Texts.hiddenRun(count),
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
                Texts.SUSPECT_WARNING,
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
            Text(Texts.LINKS_DISABLED, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Texts.timestamp(message.dateReceived), style = MaterialTheme.typography.labelSmall)
            if (onRescue != null) {
                TextButton(onClick = onRescue) { Text(Texts.RESCUE) }
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
        title = { Text(Texts.WHY_HERE) },
        text = {
            Column {
                Text(Texts.reason(message))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "${Texts.folderName(message.folder)} · ${Texts.sender(message.address)}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (isAdLine) {
                    Text(Texts.UNSUB, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            if (message.folder != Folder.INBOX) {
                TextButton(onClick = onRescue) { Text(Texts.RESCUE) }
            } else {
                TextButton(onClick = onBlock) { Text(Texts.BLOCK) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Texts.CANCEL) }
        },
    )
}
