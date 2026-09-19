package ir.asudehapp.sms.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.telephony.SimCard
import ir.asudehapp.sms.telephony.SimCards

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

/** کارهای نوار بالای گفتگو: سنجاق، `Unsub11` و خوانده‌نشده. */
@Composable
fun ConversationActions(model: AsudehViewModel, state: ConversationUiState, folder: Folder) {
    var menu by remember { mutableStateOf(false) }
    var confirmUnsub by remember { mutableStateOf(false) }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (state.threadId > 0) {
            DropdownMenuItem(
                text = { Text(stringResource(if (state.pinned) R.string.unpin else R.string.pin)) },
                onClick = {
                    menu = false
                    model.setPinned(state.threadId, !state.pinned)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_unread)) },
                onClick = {
                    menu = false
                    model.markUnread(state.threadId, folder)
                    model.back()
                },
            )
        }
        // `Unsub11` فقط برای خطوط انبوه، و همیشه با تأیید صریح (D28).
        if (state.isAdLine) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unsub)) },
                onClick = {
                    menu = false
                    confirmUnsub = true
                },
            )
        }
    }
    if (confirmUnsub) {
        AlertDialog(
            onDismissRequest = { confirmUnsub = false },
            title = { Text(stringResource(R.string.unsub_confirm_title)) },
            text = { Text(stringResource(R.string.unsub_confirm_body, Texts.sender(state.address))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsub = false
                    model.unsubscribe()
                }) { Text(stringResource(R.string.unsub_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsub = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun ConversationScreen(model: AsudehViewModel, isDefaultApp: Boolean) {
    val state by model.conversation.collectAsState()
    val draft by model.draft.collectAsState()
    val attachments by model.attachments.collectAsState()
    val preparing by model.preparingAttachment.collectAsState()
    val sendError by model.sendError.collectAsState()
    val settings by model.settings.collectAsState()
    val sims by model.sims.collectAsState()
    var expandedRuns by remember { mutableStateOf(setOf<Long>()) }
    var reasonFor by remember { mutableStateOf<MessageEntity?>(null) }

    val items = remember(state.messages, state.folder) {
        buildItems(state.messages, state.folder)
    }
    // نشان سیم فقط وقتی معنی دارد که پیامک‌ها از بیش از یک سیم آمده باشند (D27).
    val multiSim = remember(state.messages, sims) {
        sims.size > 1 || state.messages.map { it.subId }.filter { it >= 0 }.distinct().size > 1
    }
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(items.size) { position ->
                when (val item = items[position]) {
                    is ConversationItem.Visible -> MessageBubble(
                        model = model,
                        message = item.message,
                        dimmed = false,
                        showSender = state.isGroup,
                        showSim = multiSim,
                        showReason = settings.showReasonEverywhere,
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
                                    model = model,
                                    message = hidden,
                                    dimmed = true,
                                    showSender = state.isGroup,
                                    showSim = multiSim,
                                    showReason = true,
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
            model = model,
            state = state,
            draft = draft,
            attachments = attachments,
            preparing = preparing,
            sims = sims,
            multiSim = multiSim,
            enabled = isDefaultApp && state.participants.isNotEmpty(),
            isDefaultApp = isDefaultApp,
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
    model: AsudehViewModel,
    state: ConversationUiState,
    draft: String,
    attachments: List<PendingAttachment>,
    preparing: Boolean,
    sims: List<SimCard>,
    multiSim: Boolean,
    enabled: Boolean,
    isDefaultApp: Boolean,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(model::attach)
    }
    var simMenu by remember { mutableStateOf(false) }
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            model.refreshSims()
            simMenu = true
        }
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (!isDefaultApp) {
            Text(
                stringResource(R.string.send_not_default),
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.isGroup && enabled) {
            Text(
                stringResource(R.string.new_conversation_group_hint),
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (preparing) {
            Text(stringResource(R.string.attachment_preparing), style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments) { attachment ->
                    Box {
                        val bitmap = remember(attachment) {
                            runCatching {
                                android.graphics.BitmapFactory.decodeByteArray(attachment.part.data, 0, attachment.part.data.size)
                                    ?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap,
                                null,
                                Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        IconButton(
                            onClick = { model.removeAttachment(attachment) },
                            modifier = Modifier.size(28.dp).align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove_attachment))
                        }
                    }
                }
            }
        }
        // انتخاب سیم فقط وقتی بیش از یک سیم هست (D27). بدون اجازهٔ خواندن
        // سیم‌کارت‌ها، از روی پیامک‌های همین گفتگو حدس زده می‌شود.
        if (enabled && multiSim) {
            val current = sims.firstOrNull { it.subscriptionId == state.subscriptionId }
            val slot = current?.slot ?: SimCards.slotOf(context, state.subscriptionId)
            Box {
                AssistChip(
                    onClick = {
                        if (SimCards.canRead(context)) {
                            model.refreshSims()
                            simMenu = true
                        } else {
                            phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.send_from_sim,
                                slot?.let { Texts.digits(stringResource(R.string.sim_label, it)) }
                                    ?: stringResource(R.string.choose_sim),
                            ),
                        )
                    },
                )
                DropdownMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                    for (sim in sims) {
                        DropdownMenuItem(
                            text = {
                                Text(Texts.digits(stringResource(R.string.sim_label, sim.slot)) + " · " + sim.label)
                            },
                            onClick = {
                                simMenu = false
                                model.chooseSim(sim.subscriptionId)
                            },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = enabled && !preparing,
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.attach_image))
            }
            OutlinedTextField(
                value = draft,
                onValueChange = model::updateDraft,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.compose_hint)) },
                maxLines = 5,
            )
            IconButton(
                onClick = model::send,
                enabled = enabled && !preparing && (draft.isNotBlank() || attachments.isNotEmpty()),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
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
    model: AsudehViewModel,
    message: MessageEntity,
    dimmed: Boolean,
    showSender: Boolean,
    showSim: Boolean,
    showReason: Boolean,
    onWhy: () -> Unit,
    onResend: () -> Unit,
    onRescue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
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
        if (showSender && !message.outgoing) {
            Text(Texts.sender(message.address), style = MaterialTheme.typography.labelMedium)
        }
        Column(
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (message.kind == MessageEntity.KIND_MMS) {
                MmsContent(model, message)
            }
            // متن پیامک هرگز تغییر نمی‌کند: نه ارقامش و نه چیز دیگر (D50).
            if (message.body.isNotEmpty()) {
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
        }
        if (message.risk) {
            Text(stringResource(R.string.links_disabled), style = MaterialTheme.typography.labelSmall)
        }
        if (showReason && !message.outgoing) {
            Text(Texts.reason(message), style = MaterialTheme.typography.labelSmall)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Texts.timestamp(message.dateReceived), style = MaterialTheme.typography.labelSmall)
            if (showSim) {
                SimCards.slotOf(context, message.subId)?.let { slot ->
                    Text(
                        Texts.digits(stringResource(R.string.sim_label, slot)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
                SendStatus.DELIVERED -> Text(
                    stringResource(R.string.delivered),
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
            if (message.outgoing || message.isGroup) {
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
