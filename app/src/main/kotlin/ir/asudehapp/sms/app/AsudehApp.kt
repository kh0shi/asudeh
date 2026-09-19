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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.model.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsudehApp(
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val destination by model.destination.collectAsState()

    BackHandler(enabled = destination != Destination.Home) { model.back() }

    val conversation by model.conversation.collectAsState()
    val title = when (val current = destination) {
        Destination.Home -> stringResource(R.string.app_name)
        is Destination.FolderView -> Texts.folderName(current.folder)
        is Destination.Conversation -> Texts.sender(conversation.address)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (destination != Destination.Home) {
                        TextButton(onClick = { model.back() }) {
                            Text(stringResource(R.string.folder_inbox))
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
                    onOpenFolder = { model.navigate(Destination.FolderView(it)) },
                    onOpenThread = {
                        model.navigate(Destination.Conversation(it, Folder.INBOX))
                    },
                )

                is Destination.FolderView -> FolderScreen(
                    model = model,
                    folder = current.folder,
                    isDefaultApp = isDefaultApp,
                    onOpenThread = {
                        model.navigate(Destination.Conversation(it, current.folder))
                    },
                )

                is Destination.Conversation -> ConversationScreen(
                    model = model,
                    isDefaultApp = isDefaultApp,
                )
            }
        }
    }

    RescueFollowUpDialog(model)
    EmptyFolderDialog(model)
}

@Composable
private fun RescueFollowUpDialog(model: AsudehViewModel) {
    val pending by model.rescueFollowUp.collectAsState()
    val followUp = pending ?: return

    // برای `MixedSender` پاسخ برجسته «فقط همین» است (ADR-0006 بند ۱).
    AlertDialog(
        onDismissRequest = { model.answerRescueFollowUp(alwaysAllow = false) },
        title = { Text(stringResource(R.string.rescue_follow_up)) },
        text = {
            Text(
                if (followUp.mixedSender) {
                    stringResource(R.string.mixed_sender)
                } else {
                    Texts.sender(followUp.address)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { model.answerRescueFollowUp(alwaysAllow = !followUp.mixedSender) }) {
                Text(stringResource(if (followUp.mixedSender) R.string.only_this else R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { model.answerRescueFollowUp(alwaysAllow = followUp.mixedSender) }) {
                Text(stringResource(if (followUp.mixedSender) R.string.yes else R.string.only_this))
            }
        },
    )
}

/**
 * «خالی کردن پوشه» (D45): تنها حذف گروهی اپ، با دو مرحلهٔ تأیید و نمایش تعداد
 * در هر دو مرحله (ADR-0003 بند ۳).
 */
@Composable
private fun EmptyFolderDialog(model: AsudehViewModel) {
    val pending by model.emptyFolder.collectAsState()
    val request = pending ?: return

    if (request.count == 0) {
        AlertDialog(
            onDismissRequest = model::cancelEmptyFolder,
            title = { Text(stringResource(R.string.empty_folder)) },
            text = { Text(stringResource(R.string.empty_folder_nothing)) },
            confirmButton = {
                TextButton(onClick = model::cancelEmptyFolder) { Text(stringResource(R.string.cancel)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = model::cancelEmptyFolder,
        title = { Text(stringResource(R.string.empty_folder)) },
        text = {
            Text(
                if (request.confirmed) {
                    Texts.count(R.plurals.empty_folder_step2, request.count)
                } else {
                    Texts.count(
                        R.plurals.empty_folder_step1,
                        request.count,
                        Texts.folderName(request.folder),
                    )
                },
            )
        },
        confirmButton = {
            if (request.confirmed) {
                TextButton(onClick = model::emptyFolderConfirmed) {
                    Text(stringResource(R.string.confirm_delete))
                }
            } else {
                TextButton(onClick = model::confirmEmptyFolderFirstStep) {
                    Text(stringResource(R.string.continue_))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = model::cancelEmptyFolder) { Text(stringResource(R.string.cancel)) }
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
    val syncFailed by model.syncFailed.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (!isDefaultApp) {
            item { DefaultAppCard(onBecomeDefault) }
        }
        if (syncing || syncFailed) {
            item {
                Text(
                    stringResource(if (syncing) R.string.syncing else R.string.sync_failed),
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // یک ردیف ثابت بالای فهرست، بدون تب و بدون منوی کشویی (D42 الف).
        item {
            FolderRow(
                title = stringResource(R.string.folder_promo),
                trailing = if (promoUnread > 0) Texts.count(R.plurals.new_count, promoUnread) else "",
                onClick = { onOpenFolder(Folder.PROMO) },
            )
        }
        // ردیف کلاهبرداری فقط وقتی دیده می‌شود که پوشه خالی نباشد (D42 الف).
        if (scamCount > 0) {
            item {
                FolderRow(
                    title = stringResource(R.string.folder_scam),
                    trailing = Texts.count(R.plurals.new_count, scamCount),
                    onClick = { onOpenFolder(Folder.SCAM) },
                )
            }
        }
        item { HorizontalDivider() }

        if (threads.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_inbox)) }
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
    isDefaultApp: Boolean,
    onOpenThread: (Long) -> Unit,
) {
    val threads by when (folder) {
        Folder.PROMO -> model.promoThreads
        Folder.SCAM -> model.scamThreads
        Folder.INBOX -> model.inboxThreads
    }.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // «خالی کردن پوشه» فقط در `PromoFolder` است (D45)، و حذف از provider فقط
        // برای اپ پیش‌فرض ممکن است.
        if (isDefaultApp && folder == Folder.PROMO) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { model.requestEmptyFolder(folder) }) {
                    Text(stringResource(R.string.empty_folder))
                }
            }
            HorizontalDivider()
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (threads.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(
                            if (folder == Folder.SCAM) R.string.empty_scam else R.string.empty_promo,
                        ),
                    )
                }
            }
            items(threads, key = { it.threadId }) { thread ->
                ThreadRow(thread) { onOpenThread(thread.threadId) }
            }
        }
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
            Text(stringResource(R.string.not_default_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.not_default_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onBecomeDefault) { Text(stringResource(R.string.become_default)) }
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
                        Texts.count(R.plurals.new_count, thread.unread),
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
