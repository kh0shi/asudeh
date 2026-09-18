package ir.asudehapp.sms.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.model.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsudehApp(
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
    initialThreadId: Long = 0,
) {
    var destination by remember {
        mutableStateOf<Destination>(
            if (initialThreadId > 0) {
                Destination.Conversation(initialThreadId, Folder.INBOX)
            } else {
                Destination.Home
            },
        )
    }

    LaunchedEffect(Unit) { model.sync() }
    LaunchedEffect(destination) {
        (destination as? Destination.Conversation)?.let {
            model.openConversation(it.threadId, it.folder)
        }
    }

    BackHandler(enabled = destination != Destination.Home) { destination = Destination.Home }

    val conversation by model.conversation.collectAsState()
    val title = when (val current = destination) {
        Destination.Home -> Texts.APP_NAME
        is Destination.FolderView -> Texts.folderName(current.folder)
        is Destination.Conversation -> Texts.sender(conversation.address)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (destination != Destination.Home) {
                        TextButton(onClick = { destination = Destination.Home }) {
                            Text(Texts.INBOX)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = destination) {
                Destination.Home -> HomeScreen(
                    model = model,
                    isDefaultApp = isDefaultApp,
                    onBecomeDefault = onBecomeDefault,
                    onOpenFolder = { destination = Destination.FolderView(it) },
                    onOpenThread = { destination = Destination.Conversation(it, Folder.INBOX) },
                )

                is Destination.FolderView -> FolderScreen(
                    model = model,
                    folder = current.folder,
                    onOpenThread = { destination = Destination.Conversation(it, current.folder) },
                )

                is Destination.Conversation -> ConversationScreen(model = model)
            }
        }
    }

    RescueFollowUpDialog(model)
}

@Composable
private fun RescueFollowUpDialog(model: AsudehViewModel) {
    val pending by model.rescueFollowUp.collectAsState()
    val followUp = pending ?: return

    // برای `MixedSender` پاسخ برجسته «فقط همین» است (ADR-0006 بند ۱).
    AlertDialog(
        onDismissRequest = { model.answerRescueFollowUp(alwaysAllow = false) },
        title = { Text(Texts.RESCUE_FOLLOW_UP) },
        text = {
            Text(
                if (followUp.mixedSender) {
                    "این فرستنده هم پیامک مهم می‌فرستد و هم تبلیغ."
                } else {
                    Texts.sender(followUp.address)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { model.answerRescueFollowUp(alwaysAllow = !followUp.mixedSender) }) {
                Text(if (followUp.mixedSender) Texts.ONLY_THIS else Texts.YES)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.answerRescueFollowUp(alwaysAllow = followUp.mixedSender) }) {
                Text(if (followUp.mixedSender) Texts.YES else Texts.ONLY_THIS)
            }
        },
    )
}

@Composable
private fun HomeScreen(
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenThread: (Long) -> Unit,
) {
    val threads by model.inboxThreads.collectAsState()
    val promoUnread by model.promoUnread.collectAsState()
    val scamCount by model.scamCount.collectAsState()
    val syncing by model.syncing.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (!isDefaultApp) {
            item { DefaultAppCard(onBecomeDefault) }
        }
        if (syncing) {
            item {
                Text(
                    Texts.SYNCING,
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // یک ردیف ثابت بالای فهرست، بدون تب و بدون منوی کشویی (D42 الف).
        item {
            FolderRow(
                title = Texts.PROMO_FOLDER,
                trailing = if (promoUnread > 0) Texts.newCount(promoUnread) else "",
                onClick = { onOpenFolder(Folder.PROMO) },
            )
        }
        // ردیف کلاهبرداری فقط وقتی دیده می‌شود که پوشه خالی نباشد (D42 الف).
        if (scamCount > 0) {
            item {
                FolderRow(
                    title = Texts.SCAM_FOLDER,
                    trailing = Texts.newCount(scamCount),
                    onClick = { onOpenFolder(Folder.SCAM) },
                )
            }
        }
        item { HorizontalDivider() }

        if (threads.isEmpty()) {
            item { EmptyState(Texts.EMPTY_INBOX) }
        }
        items(threads, key = { it.threadId }) { thread ->
            ThreadRow(thread) { onOpenThread(thread.threadId) }
        }
    }
}

@Composable
private fun FolderScreen(
    model: AsudehViewModel,
    folder: Folder,
    onOpenThread: (Long) -> Unit,
) {
    val threads by when (folder) {
        Folder.PROMO -> model.promoThreads
        Folder.SCAM -> model.scamThreads
        Folder.INBOX -> model.inboxThreads
    }.collectAsState()

    var confirmingEmpty by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { confirmingEmpty = true }) { Text(Texts.EMPTY_FOLDER) }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            if (threads.isEmpty()) {
                item {
                    EmptyState(
                        if (folder == Folder.SCAM) Texts.EMPTY_SCAM else Texts.EMPTY_PROMO,
                    )
                }
            }
            items(threads, key = { it.threadId }) { thread ->
                ThreadRow(thread) { onOpenThread(thread.threadId) }
            }
        }
    }

    if (confirmingEmpty) {
        // تنها حذف گروهی اپ، با تأیید دومرحله‌ای (D45، ADR-0003 بند ۳).
        AlertDialog(
            onDismissRequest = { confirmingEmpty = false },
            title = { Text(Texts.EMPTY_FOLDER) },
            text = { Text(Texts.EMPTY_FOLDER_CONFIRM) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingEmpty = false
                        model.emptyFolder(folder)
                    },
                ) { Text(Texts.CONFIRM_DELETE) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEmpty = false }) { Text(Texts.CANCEL) }
            },
        )
    }
}

@Composable
private fun DefaultAppCard(onBecomeDefault: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(Texts.NOT_DEFAULT_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(Texts.NOT_DEFAULT_BODY, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onBecomeDefault) { Text(Texts.BECOME_DEFAULT) }
        }
    }
}

@Composable
private fun FolderRow(title: String, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (trailing.isNotEmpty()) {
            Text(trailing, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thread.hasRisk) {
                    Text("⚠ ", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    Texts.sender(thread.address),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                thread.snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Texts.timestamp(thread.lastDate), style = MaterialTheme.typography.labelSmall)
            if (thread.unread > 0) {
                Box(
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        Texts.newCount(thread.unread),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
