package ir.asudehapp.sms.app

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MessageKey
import ir.asudehapp.sms.data.ScheduleState
import ir.asudehapp.sms.data.ScheduledMessageEntity
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
                text = { Text(stringResource(if (state.muted) R.string.unmute else R.string.mute)) },
                onClick = {
                    menu = false
                    model.setMuted(state.threadId, !state.muted)
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
    val selected by model.selectedMessages.collectAsState()
    val scheduled by model.scheduledHere.collectAsState()
    var expandedRuns by remember { mutableStateOf(setOf<Long>()) }
    var reasonFor by remember { mutableStateOf<MessageEntity?>(null) }
    var detailsFor by remember { mutableStateOf<MessageEntity?>(null) }
    // انتخاب بخشی از متن در جای خودِ پیامک انجام می‌شود، نه در یک مودال؛ این
    // فقط می‌گوید کدام پیامک الان در آن حال است (هر بار یکی). با عوض شدن
    // گفتگو از نو شروع می‌شود.
    var pickingTextIn by remember(state.threadId) { mutableStateOf<MessageKey?>(null) }
    // راهنمای «با کشیدن دستگیره‌ها…» فقط بار اول؛ بعد از آن خودِ دستگیره‌ها
    // پیداست و فقط «تمام» می‌ماند.
    val hintSeen by model.textPickHintSeen.collectAsState()
    var showPickHint by remember { mutableStateOf(false) }
    val pickText = { key: MessageKey ->
        pickingTextIn = key
        showPickHint = !hintSeen
        model.markTextPickHintSeen()
    }
    // چون مودالی در کار نیست، «بازگشت» باید همین حال را ببندد؛ پیش از
    // BackHandlerِ صفحهٔ اصلی می‌نشیند، پس انتخاب پیامک دست‌نخورده می‌ماند.
    BackHandler(enabled = pickingTextIn != null) { pickingTextIn = null }

    val items = remember(state.messages, state.folder) {
        buildItems(state.messages, state.folder)
    }
    // نشان سیم فقط وقتی معنی دارد که پیامک‌ها از بیش از یک سیم آمده باشند (D27).
    val multiSim = remember(state.messages, sims) {
        sims.size > 1 || state.messages.map { it.subId }.filter { it >= 0 }.distinct().size > 1
    }
    val listState = rememberLazyListState()
    LaunchedEffect(items.size, scheduled.size) {
        val last = items.size + scheduled.size - 1
        if (last >= 0) listState.scrollToItem(last)
    }

    // جای کیبورد از خود Compose گرفته می‌شود، چون پنجره تا لبه‌ها کشیده شده و
    // با باز شدن کیبورد کوچک نمی‌شود (MainActivity). فاصلهٔ نوار ناوبری را
    // `Scaffold` داده و `consumeWindowInsets` آن را کم کرده است، پس اینجا دو
    // بار حساب نمی‌شود.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
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
                        showClock = settings.showMessageClock,
                        selected = MessageKey(item.message.kind, item.message.providerId) in selected,
                        selectionMode = selected.isNotEmpty(),
                        pickingText = pickingTextIn == MessageKey(item.message.kind, item.message.providerId),
                        showPickHint = showPickHint,
                        isDefaultApp = isDefaultApp,
                        onWhy = { reasonFor = item.message },
                        onDetails = { detailsFor = item.message },
                        onPickText = {
                            pickText(MessageKey(item.message.kind, item.message.providerId))
                        },
                        onDonePickingText = { pickingTextIn = null },
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
                                    showClock = settings.showMessageClock,
                                    selected = MessageKey(hidden.kind, hidden.providerId) in selected,
                                    selectionMode = selected.isNotEmpty(),
                                    pickingText = pickingTextIn == MessageKey(hidden.kind, hidden.providerId),
                                    showPickHint = showPickHint,
                                    isDefaultApp = isDefaultApp,
                                    onWhy = { reasonFor = hidden },
                                    onDetails = { detailsFor = hidden },
                                    onPickText = { pickText(MessageKey(hidden.kind, hidden.providerId)) },
                                    onDonePickingText = { pickingTextIn = null },
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
            items(scheduled.size) { position ->
                ScheduledRow(
                    message = scheduled[position],
                    enabled = isDefaultApp,
                    onCancel = { model.cancelScheduled(scheduled[position]) },
                    onSendNow = { model.sendScheduledNow(scheduled[position]) },
                )
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
            // بدون MMS، گفتگوی گروهی فرستادنی نیست: گروه در هر حال MMS است (D26).
            enabled = isDefaultApp && state.participants.isNotEmpty() &&
                (!state.isGroup || settings.mmsSending),
            isDefaultApp = isDefaultApp,
            mmsSending = settings.mmsSending,
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

    detailsFor?.let { message ->
        DetailsDialog(message = message, onDismiss = { detailsFor = null })
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
    mmsSending: Boolean,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(model::attach)
    }
    // ویدیو، صدا و کارت مخاطب: انتخابگر فایل عمومی اندروید، بدون مجوز تازه.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(model::attach)
    }
    var simMenu by remember { mutableStateOf(false) }
    var sendMenu by remember { mutableStateOf(false) }
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
        if (state.isGroup && isDefaultApp && !mmsSending) {
            Text(
                stringResource(R.string.mms_sending_off),
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
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
                            if (!attachment.part.contentType.startsWith("image/")) return@remember null
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
                        } else {
                            // ویدیو، صدا یا کارت مخاطب: به‌جای پیش‌نمایش، فقط نام فایل.
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    attachment.part.name.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
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
            // پیوست یعنی MMS؛ با خاموش بودن MMS این دکمه‌ها هم نیستند.
            if (mmsSending) {
                IconButton(
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    enabled = enabled && !preparing,
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.attach_image))
                }
                val attachFileLabel = stringResource(R.string.attach_file)
                IconButton(
                    onClick = { pickFile.launch("*/*") },
                    enabled = enabled && !preparing,
                ) {
                    // آیکون اختصاصی «فایل» در بستهٔ core نیست؛ مثل «snippet_attachment»
                    // از همان نویسهٔ گیره‌کاغذ استفاده می‌شود، نه بستهٔ سنگین آیکون‌های
                    // گسترده (اصل ۷: حجم اپ).
                    Text("📎", Modifier.semantics { contentDescription = attachFileLabel })
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = model::updateDraft,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.compose_hint)) },
                maxLines = 5,
            )
            SendButton(
                enabled = enabled && !preparing && (draft.isNotBlank() || attachments.isNotEmpty()),
                // «بعداً بفرست» فقط برای متن است؛ پیوست زمان‌بندی نمی‌شود (ADR-0011).
                canSchedule = enabled && !preparing && draft.isNotBlank() && attachments.isEmpty(),
                menuOpen = sendMenu,
                onSend = model::send,
                onOpenMenu = { sendMenu = true },
                onDismissMenu = { sendMenu = false },
                onSchedule = {
                    sendMenu = false
                    model.requestSchedule()
                },
            )
        }
    }
}

/**
 * دکمهٔ ارسال. «بعداً بفرست» دکمهٔ جدا ندارد: با نگه داشتن همین دکمه دیده
 * می‌شود، چون کار همیشگی فرستادن است و زمان‌بندی استثناست.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SendButton(
    enabled: Boolean,
    canSchedule: Boolean,
    menuOpen: Boolean,
    onSend: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSchedule: () -> Unit,
) {
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled || canSchedule,
                    onClick = { if (enabled) onSend() },
                    onLongClick = { if (canSchedule) onOpenMenu() },
                    role = Role.Button,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                stringResource(R.string.send),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                },
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.schedule_send)) },
                onClick = onSchedule,
            )
        }
    }
}

/** پررنگی دکمهٔ خاموش، همان عددی که Material 3 برای محتوای غیرفعال دارد. */
private const val DISABLED_ALPHA = 0.38f

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

/**
 * یک پیامک در گفتگو.
 *
 * لمس کوتاه منوی خود پیامک را باز می‌کند (کپی، اشتراک‌گذاری، حذف، «چرا
 * اینجاست؟»)، چون کاری که کاربر بیشتر می‌خواهد همین است. لمس طولانی مثل تلگرام
 * پیامک را انتخاب می‌کند، و لمس طولانیِ دوباره روی پیامکِ انتخاب‌شده، انتخاب
 * بخشی از متن را روشن می‌کند. در حالت انتخاب، لمس کوتاه هم فقط انتخاب را عوض
 * می‌کند و هیچ صفحهٔ دیگری باز نمی‌شود.
 *
 * با [pickingText] متنِ **همین حباب** دستگیرهٔ انتخاب می‌گیرد و هیچ مودالی باز
 * نمی‌شود: کاربر همان‌جا که متن را می‌بیند تکه‌اش را برمی‌دارد. تا وقتی این حال
 * روشن است، لمس روی حباب به خود متن می‌رسد نه به منو، وگرنه کشیدن دستگیره‌ها
 * ممکن نبود.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    model: AsudehViewModel,
    message: MessageEntity,
    dimmed: Boolean,
    showSender: Boolean,
    showSim: Boolean,
    showReason: Boolean,
    showClock: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    pickingText: Boolean,
    showPickHint: Boolean,
    isDefaultApp: Boolean,
    onWhy: () -> Unit,
    onDetails: () -> Unit,
    onPickText: () -> Unit,
    onDonePickingText: () -> Unit,
    onResend: () -> Unit,
    onRescue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { model.toggleMessage(message) })
            }
            Box {
                val bubble = Modifier
                    .background(
                        if (message.outgoing) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        RoundedCornerShape(12.dp),
                    )
                Column(
                    if (pickingText) {
                        bubble.padding(12.dp)
                    } else {
                        bubble
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) model.toggleMessage(message) else menu = true
                                },
                                // نگه داشتن: انتخاب. نگه داشتن روی پیامکِ انتخاب‌شده:
                                // برداشتن بخشی از متن (مثل تلگرام).
                                onLongClick = {
                                    if (selected) onPickText() else model.toggleMessage(message)
                                },
                            )
                            .padding(12.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.kind == MessageEntity.KIND_MMS) {
                        MmsContent(model, message)
                    }
                    if (message.body.isNotEmpty()) {
                        // `SelectionContainer` فقط در حال برداشتن متن دور متن
                        // می‌پیچد؛ وگرنه دستگیره‌هایش جلوی لمس حباب را می‌گرفت.
                        if (pickingText) {
                            SelectionContainer { MessageBody(message.body, dimmed) }
                        } else {
                            MessageBody(message.body, dimmed)
                        }
                    }
                }
                MessageMenu(
                    expanded = menu,
                    message = message,
                    canDelete = isDefaultApp,
                    onDismiss = { menu = false },
                    onCopy = { clipboard.setText(AnnotatedString(message.body)) },
                    onPickText = onPickText,
                    onShare = { shareText(context, message.body) },
                    onForward = { model.forward(message.body) },
                    onSelect = { model.toggleMessage(message) },
                    onDelete = {
                        model.toggleMessage(message)
                        model.requestDeleteSelectedMessages()
                    },
                    onWhy = onWhy,
                    onDetails = onDetails,
                )
            }
        }
        // راه بیرون آمدن، همان‌جا زیر حباب — به‌جای دکمهٔ «بستن» یک مودال.
        // راهنما فقط بار اول کنارش می‌آید.
        if (pickingText) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPickHint) {
                    Text(
                        stringResource(R.string.select_text_hint),
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = onDonePickingText) {
                    Text(stringResource(R.string.select_text_done))
                }
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
            Text(
                Texts.timestamp(message.dateReceived, withClock = showClock),
                style = MaterialTheme.typography.labelSmall,
            )
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
                // یک تیک: فرستاده شد. دو تیک: به گیرنده رسید (D53).
                SendStatus.SENT -> Tick(SENT_TICK, stringResource(R.string.sent), dim = true)
                SendStatus.DELIVERED -> Tick(DELIVERED_TICK, stringResource(R.string.delivered), dim = false)
                SendStatus.NONE -> Unit
            }
            if (onRescue != null) {
                TextButton(onClick = onRescue) { Text(stringResource(R.string.rescue)) }
            }
        }
    }
}

/** متن پیامک هرگز تغییر نمی‌کند: نه ارقامش و نه چیز دیگر (D50). */
@Composable
private fun MessageBody(body: String, dimmed: Boolean) {
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = if (dimmed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

/**
 * وضعیت ارسال، به‌صورت تیک. خود نویسه خوانده نمی‌شود، پس متنش برای صفحه‌خوان
 * جداگانه گفته می‌شود.
 */
@Composable
private fun Tick(glyph: String, label: String, dim: Boolean) {
    Text(
        glyph,
        Modifier.semantics { contentDescription = label },
        style = MaterialTheme.typography.labelSmall,
        color = if (dim) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
}

/** یک تیک برای «فرستاده شد» و دو تیک برای «تحویل شد». */
private const val SENT_TICK = "✓"
private const val DELIVERED_TICK = "✓✓"

/**
 * منوی خود پیامک، روی لمس کوتاه. فوروارد و حذف هم اینجا هستند و هم روی نوار
 * بالا در حالت انتخاب، چون کاربر هر دو راه را امتحان می‌کند.
 */
@Composable
private fun MessageMenu(
    expanded: Boolean,
    message: MessageEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPickText: () -> Unit,
    onShare: () -> Unit,
    onForward: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onWhy: () -> Unit,
    onDetails: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (message.body.isNotEmpty()) {
            MessageMenuItem(stringResource(R.string.copy_message)) {
                onDismiss()
                onCopy()
            }
            MessageMenuItem(stringResource(R.string.select_text)) {
                onDismiss()
                onPickText()
            }
            MessageMenuItem(stringResource(R.string.forward)) {
                onDismiss()
                onForward()
            }
            MessageMenuItem(stringResource(R.string.share_message)) {
                onDismiss()
                onShare()
            }
        }
        MessageMenuItem(stringResource(R.string.select)) {
            onDismiss()
            onSelect()
        }
        // حذف از provider فقط برای اپ پیش‌فرض ممکن است.
        if (canDelete) {
            MessageMenuItem(stringResource(R.string.delete)) {
                onDismiss()
                onDelete()
            }
        }
        if (!message.outgoing) {
            MessageMenuItem(stringResource(R.string.why_here)) {
                onDismiss()
                onWhy()
            }
        }
        MessageMenuItem(stringResource(R.string.message_details)) {
            onDismiss()
            onDetails()
        }
    }
}

/** یک گزینه در منوی پیامک. */
@Composable
private fun MessageMenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

/**
 * یک پیامک زمان‌بندی‌شده، زیر آخرین پیام گفتگو. تا وقتی فرستاده نشده، فقط
 * همین‌جاست: در Telephony Provider نوشته نمی‌شود، چون هنوز پیامکی نیست
 * (ADR-0011).
 */
@Composable
private fun ScheduledRow(
    message: ScheduledMessageEntity,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSendNow: () -> Unit,
) {
    val missed = message.state == ScheduleState.MISSED
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            Modifier
                .background(
                    if (missed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (missed) stringResource(R.string.schedule_missed) else Texts.scheduledFor(message.sendAt),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.schedule_cancel)) }
            TextButton(onClick = onSendNow, enabled = enabled) {
                Text(stringResource(R.string.schedule_send_now))
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

/**
 * «جزئیات پیامک»: زمان دقیق ارسال و دریافت (بدون خلاصه شدن به فقط-ساعت)، سیم
 * فرستنده یا گیرنده، و نوع پیامک. همهٔ این‌ها روی `MessageEntity` هست؛ اینجا
 * فقط یک‌جا نشانشان می‌دهد.
 */
@Composable
private fun DetailsDialog(message: MessageEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val slot = remember(message.subId) { SimCards.slotOf(context, message.subId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeLabel = stringResource(
                    if (message.kind == MessageEntity.KIND_MMS) R.string.details_type_mms else R.string.details_type_sms,
                )
                DetailRow(stringResource(R.string.details_type), typeLabel)
                DetailRow(stringResource(R.string.details_sent_at), Texts.exactTimestamp(message.date))
                DetailRow(stringResource(R.string.details_received_at), Texts.exactTimestamp(message.dateReceived))
                if (slot != null) {
                    val simLabel = Texts.digits(stringResource(R.string.sim_label, slot))
                    DetailRow(stringResource(R.string.details_sim), simLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** «اشتراک‌گذاری» متن پیامک با اپ‌های دیگر؛ خود اپ چیزی نمی‌فرستد. */
private fun shareText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
