package ir.asudehapp.sms.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import ir.asudehapp.sms.data.BackupException
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MoveSuggestion
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.model.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsudehApp(
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
    onContinueReadOnly: () -> Unit,
    onExit: () -> Unit,
) {
    val onboarded by model.onboarded.collectAsState()
    if (!onboarded) {
        WelcomeScreen(onBecomeDefault = onBecomeDefault, onContinueReadOnly = onContinueReadOnly)
        return
    }

    val destination by model.destination.collectAsState()
    val conversation by model.conversation.collectAsState()
    val selectedMessages by model.selectedMessages.collectAsState()
    val selectedThreads by model.selectedThreads.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val notice by model.notice.collectAsState()
    val noticeText = notice?.let { noticeText(it) }
    // «بازگرداندن» فقط روی همان حذفی که همین الان انجام شد (ADR-0010).
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(notice) {
        val current = notice
        if (noticeText != null) {
            val undoable = current as? UiNotice.Deleted
            val result = snackbar.showSnackbar(
                message = noticeText,
                actionLabel = undoLabel.takeIf { undoable != null },
            )
            if (result == SnackbarResult.ActionPerformed && undoable != null) {
                model.restoreFromTrash(undoable.trashIds)
            } else {
                model.dismissNotice()
            }
        }
    }

    // در حالت انتخاب، «بازگشت» اول انتخاب را برمی‌دارد.
    val selectionCount = if (destination is Destination.Conversation) {
        selectedMessages.size
    } else {
        selectedThreads.size
    }
    BackHandler {
        when {
            selectionCount > 0 -> clearSelection(model, destination)
            !model.back() -> onExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionCount > 0) {
                            Texts.count(R.plurals.selected_count, selectionCount)
                        } else {
                            when (val current = destination) {
                                Destination.Home -> stringResource(R.string.app_name)
                                is Destination.FolderView -> Texts.folderName(current.folder)
                                is Destination.Conversation -> Texts.participants(conversation.participants)
                                Destination.Search -> stringResource(R.string.search)
                                Destination.Settings -> stringResource(R.string.settings)
                                Destination.Rules -> stringResource(R.string.settings_rules)
                                Destination.NewConversation -> stringResource(R.string.new_conversation)
                                Destination.Trash -> stringResource(R.string.trash)
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selectionCount > 0) {
                        IconButton(onClick = { clearSelection(model, destination) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    } else if (destination != Destination.Home) {
                        IconButton(onClick = { model.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (selectionCount > 0) {
                        SelectionActions(model, destination, isDefaultApp, selectedThreads)
                    } else {
                        when (val current = destination) {
                            Destination.Home -> {
                                IconButton(onClick = { model.navigate(Destination.Search) }) {
                                    Icon(Icons.Default.Search, stringResource(R.string.search))
                                }
                                IconButton(onClick = { model.navigate(Destination.Settings) }) {
                                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                                }
                            }
                            is Destination.Conversation -> ConversationActions(model, conversation, current.folder)
                            else -> Unit
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (destination == Destination.Home && isDefaultApp) {
                FloatingActionButton(onClick = { model.navigate(Destination.NewConversation) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.new_conversation))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
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

                Destination.Search -> SearchScreen(model)
                Destination.Settings -> SettingsScreen(model, isDefaultApp)
                Destination.Rules -> RulesScreen(model)
                Destination.NewConversation -> NewConversationScreen(model)
                Destination.Trash -> TrashScreen(model)
            }
        }
    }

    RescueFollowUpDialog(model)
    EmptyFolderDialog(model)
    DeleteDialog(model)
    EmptyTrashDialog(model)
    ScheduleDialog(model)
    SweepOfferDialog(model)
    SuggestionSampleDialog(model)
    CrashReportDialog(model)
}

/** برداشتن انتخاب، هر کجا که هستیم. */
private fun clearSelection(model: AsudehViewModel, destination: Destination) {
    if (destination is Destination.Conversation) {
        model.clearMessageSelection()
    } else {
        model.clearThreadSelection()
    }
}

/**
 * نوار بالا در حالت انتخاب: «انتخاب همه» و «حذف». حذف از Telephony Provider
 * فقط برای اپ پیش‌فرض ممکن است، پس دکمه‌اش فقط همان وقت دیده می‌شود.
 */
@Composable
private fun SelectionActions(
    model: AsudehViewModel,
    destination: Destination,
    isDefaultApp: Boolean,
    selectedThreads: Set<Long>,
) {
    if (destination is Destination.Conversation) {
        IconButton(onClick = model::selectAllMessages) {
            Icon(Icons.Default.Done, stringResource(R.string.select_all))
        }
    }
    if (!isDefaultApp) return
    IconButton(
        onClick = {
            when (destination) {
                is Destination.Conversation -> model.requestDeleteSelectedMessages()
                is Destination.FolderView -> model.requestDeleteThreads(selectedThreads, destination.folder)
                else -> model.requestDeleteThreads(selectedThreads, Folder.INBOX)
            }
        },
    ) {
        Icon(Icons.Default.Delete, stringResource(R.string.delete))
    }
}

/**
 * حذف انتخاب‌شده‌ها (ADR-0010). یک مرحله، چون حذف به «حذف‌شده‌ها» می‌رود و
 * برگشت‌پذیر است؛ متن همین را می‌گوید تا کاربر بداند چه اتفاقی می‌افتد.
 */
@Composable
private fun DeleteDialog(model: AsudehViewModel) {
    val pending by model.deleteRequest.collectAsState()
    val request = pending ?: return

    AlertDialog(
        onDismissRequest = model::cancelDelete,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column {
                Text(
                    if (request.threads > 0) {
                        Texts.count(R.plurals.delete_threads_body, request.threads, request.messages.size)
                    } else {
                        Texts.count(R.plurals.delete_messages_body, request.messages.size)
                    },
                )
                Text(
                    stringResource(R.string.delete_explain),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = model::confirmDelete) { Text(stringResource(R.string.delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = model::cancelDelete) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** «خالی کردن حذف‌شده‌ها»: حذف قطعی، پس مثل D45 دو مرحله دارد. */
@Composable
private fun EmptyTrashDialog(model: AsudehViewModel) {
    val pending by model.emptyTrash.collectAsState()
    val request = pending ?: return

    AlertDialog(
        onDismissRequest = model::cancelEmptyTrash,
        title = { Text(stringResource(R.string.empty_trash)) },
        text = {
            Text(
                if (request.confirmed) {
                    Texts.count(R.plurals.empty_trash_step2, request.count)
                } else {
                    Texts.count(R.plurals.empty_trash_step1, request.count)
                },
            )
        },
        confirmButton = {
            if (request.confirmed) {
                TextButton(onClick = model::emptyTrashConfirmed) {
                    Text(stringResource(R.string.confirm_delete))
                }
            } else {
                TextButton(onClick = model::confirmEmptyTrashFirstStep) {
                    Text(stringResource(R.string.continue_))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = model::cancelEmptyTrash) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** متن Snackbar برای هر پیام کوتاه. */
@Composable
private fun noticeText(notice: UiNotice): String = when (notice) {
    is UiNotice.Deleted -> Texts.count(R.plurals.notice_deleted, notice.count)
    is UiNotice.Restored -> Texts.count(R.plurals.notice_restored, notice.count)
    UiNotice.RestoreFailed -> stringResource(R.string.notice_restore_failed)
    UiNotice.NothingDeleted -> stringResource(R.string.notice_nothing_deleted)
    is UiNotice.Scheduled -> Texts.scheduledFor(notice.atMillis)
    UiNotice.ScheduleTooSoon -> stringResource(R.string.schedule_too_soon)
    UiNotice.ScheduleSending -> stringResource(R.string.notice_schedule_sending)
    is UiNotice.BackupExported -> Texts.count(R.plurals.backup_exported, notice.messages)
    is UiNotice.BackupImported -> buildString {
        append(
            stringResource(
                R.string.backup_imported,
                Texts.digits(notice.added.toString()),
                Texts.digits(notice.duplicates.toString()),
            ),
        )
        if (notice.corrupt > 0) {
            append(' ')
            append(stringResource(R.string.backup_imported_corrupt, Texts.digits(notice.corrupt.toString())))
        }
    }
    is UiNotice.BackupFailed -> stringResource(
        when (notice.reason) {
            BackupException.Reason.NOT_A_BACKUP, BackupException.Reason.EMPTY -> R.string.backup_not_a_backup
            BackupException.Reason.NEWER_VERSION -> R.string.backup_newer
            BackupException.Reason.NOT_DEFAULT_APP -> R.string.backup_not_default
            null -> R.string.backup_failed
        },
    )
    UiNotice.AttachmentFailed -> stringResource(R.string.attachment_failed)
    UiNotice.DownloadRequested -> stringResource(R.string.mms_download_requested)
    UiNotice.DownloadFailed -> stringResource(R.string.mms_download_failed)
}

/**
 * گزارش کرش قبلی (D58): پیشنهادی آرام، با متن کامل گزارش پیش از ارسال. ارسال
 * با منوی اشتراک‌گذاری اندروید است؛ خود اپ چیزی نمی‌فرستد.
 */
@Composable
private fun CrashReportDialog(model: AsudehViewModel) {
    val report by model.crashReport.collectAsState()
    val text = report ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = model::dismissCrashReport,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text,
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Asudeh crash report")
                    .putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                model.dismissCrashReport()
            }) { Text(stringResource(R.string.crash_send)) }
        },
        dismissButton = {
            TextButton(onClick = model::dismissCrashReport) { Text(stringResource(R.string.crash_dismiss)) }
        },
    )
}

/**
 * مرحلهٔ اول اولین اجرا (D47): وعدهٔ اپ و یک دکمه. اگر کاربر نخواهد، اپ
 * فقط-خواندنی کار می‌کند و از کار نمی‌افتد.
 */
@Composable
private fun WelcomeScreen(onBecomeDefault: () -> Unit, onContinueReadOnly: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.welcome_default_hint), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBecomeDefault, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.become_default))
            }
            TextButton(onClick = onContinueReadOnly, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.continue_read_only))
            }
        }
    }
}

/**
 * مرحلهٔ چهارم اولین اجرا (D47، اصل ۸): نتیجهٔ `HistorySweep` و سه گزینه.
 * هیچ پیامک قدیمی‌ای بدون «منتقل کن» جابه‌جا نمی‌شود.
 */
@Composable
private fun SweepOfferDialog(model: AsudehViewModel) {
    val offer by model.sweepOffer.collectAsState()
    val found = offer ?: return
    val sample by model.suggestionSample.collectAsState()

    AlertDialog(
        onDismissRequest = model::postponeSuggestions,
        title = { Text(stringResource(R.string.sweep_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionSummary(found)
                Text(stringResource(R.string.sweep_explain), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.show_on_doubt), style = MaterialTheme.typography.bodySmall)
                val shown = sample
                if (shown == null) {
                    TextButton(onClick = model::showSuggestionSample) {
                        Text(stringResource(R.string.sweep_samples))
                    }
                } else {
                    SampleList(shown)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = model::acceptSuggestions) { Text(stringResource(R.string.sweep_move)) }
        },
        dismissButton = {
            TextButton(onClick = model::postponeSuggestions) { Text(stringResource(R.string.sweep_not_now)) }
        },
    )
}

/** نمونه‌ها از کارت پیشنهاد در `PromoFolder`. */
@Composable
private fun SuggestionSampleDialog(model: AsudehViewModel) {
    val offer by model.sweepOffer.collectAsState()
    val sample by model.suggestionSample.collectAsState()
    val shown = sample ?: return
    if (offer != null) return

    AlertDialog(
        onDismissRequest = model::hideSuggestionSample,
        title = { Text(stringResource(R.string.sweep_samples)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { SampleList(shown) }
        },
        confirmButton = {
            TextButton(onClick = model::hideSuggestionSample) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun SuggestionSummary(found: MoveSuggestion) {
    Text(
        Texts.count(R.plurals.sweep_found, found.messages, found.threads),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (found.scams > 0) {
        Text(
            Texts.count(R.plurals.sweep_found_scams, found.scams),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SampleList(messages: List<MessageEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (message in messages) {
            Column {
                Text(Texts.sender(message.address), style = MaterialTheme.typography.titleSmall)
                Text(
                    message.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** پیشنهاد جابه‌جایی که با «فعلاً نه» در `PromoFolder` می‌ماند (اصل ۸، D22). */
@Composable
private fun SuggestionCard(model: AsudehViewModel, found: MoveSuggestion) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SuggestionSummary(found)
            Text(stringResource(R.string.sweep_explain), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = model::acceptSuggestions) { Text(stringResource(R.string.sweep_move)) }
                TextButton(onClick = model::showSuggestionSample) { Text(stringResource(R.string.sweep_samples)) }
                TextButton(onClick = model::keepSuggestionsInInbox) { Text(stringResource(R.string.sweep_keep)) }
            }
        }
    }
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
    val selectedThreads by model.selectedThreads.collectAsState()
    val promoUnread by model.promoUnread.collectAsState()
    val scamCount by model.scamCount.collectAsState()
    val syncing by model.syncing.collectAsState()
    val syncFailed by model.syncFailed.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (!isDefaultApp) {
            item { DefaultAppCard(onBecomeDefault) }
        }
        if (syncing) {
            // مرحلهٔ سوم اولین اجرا: فهرست از همین لحظه قابل استفاده است (D47).
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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

        if (threads.isEmpty() && !syncing) {
            item { EmptyState(stringResource(R.string.empty_inbox)) }
        }
        items(threads, key = { it.threadId }) { thread ->
            ThreadRow(
                model = model,
                thread = thread,
                selected = thread.threadId in selectedThreads,
                selectionMode = selectedThreads.isNotEmpty(),
                canDelete = isDefaultApp,
                onClick = { onOpenThread(thread.threadId) },
            )
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
    val selectedThreads by model.selectedThreads.collectAsState()
    val suggestions by model.suggestions.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (folder == Folder.PROMO && suggestions.messages > 0) {
            SuggestionCard(model, suggestions)
        }
        // «همه خوانده شد» و «خالی کردن پوشه» فقط در `PromoFolder` است (D45)، و
        // حذف از provider فقط برای اپ پیش‌فرض ممکن است.
        if (folder == Folder.PROMO && threads.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { model.markFolderRead(folder) }) {
                    Text(stringResource(R.string.mark_all_read))
                }
                if (isDefaultApp) {
                    TextButton(onClick = { model.requestEmptyFolder(folder) }) {
                        Text(stringResource(R.string.empty_folder))
                    }
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
                ThreadRow(
                    model = model,
                    thread = thread,
                    selected = thread.threadId in selectedThreads,
                    selectionMode = selectedThreads.isNotEmpty(),
                    canDelete = isDefaultApp,
                    onClick = { onOpenThread(thread.threadId) },
                )
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

/**
 * یک گفتگو در فهرست. لمس طولانی منوی سنجاق، خوانده/نخوانده و حذف را باز
 * می‌کند (D53، ADR-0010)؛ وقتی چند گفتگو انتخاب شده‌اند، لمس کوتاه هم انتخاب
 * را عوض می‌کند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    model: AsudehViewModel,
    thread: ThreadSummary,
    selected: Boolean,
    selectionMode: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Unspecified,
                )
                .combinedClickable(
                    onClick = { if (selectionMode) model.toggleThread(thread.threadId) else onClick() },
                    onLongClick = {
                        if (selectionMode) model.toggleThread(thread.threadId) else menu = true
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { model.toggleThread(thread.threadId) })
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thread.pinned) {
                        Icon(
                            Icons.Default.Star,
                            stringResource(R.string.pinned),
                            Modifier.size(16.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (thread.hasRisk) {
                        Text("⚠ ", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        Texts.thread(thread.address, thread.recipients),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (thread.unread > 0) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    snippet(thread),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (thread.draft.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Unspecified,
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
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (thread.pinned) R.string.unpin else R.string.pin)) },
                onClick = {
                    menu = false
                    model.setPinned(thread.threadId, !thread.pinned)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (thread.unread > 0) R.string.mark_read else R.string.mark_unread)) },
                onClick = {
                    menu = false
                    if (thread.unread > 0) {
                        model.markRead(thread.threadId, thread.folder)
                    } else {
                        model.markUnread(thread.threadId, thread.folder)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select)) },
                onClick = {
                    menu = false
                    model.toggleThread(thread.threadId)
                },
            )
            // حذف از provider فقط برای اپ پیش‌فرض ممکن است.
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_thread)) },
                    onClick = {
                        menu = false
                        model.requestDeleteThreads(setOf(thread.threadId), thread.folder)
                    },
                )
            }
        }
    }
}

/** پیش‌نمایش گفتگو: پیش‌نویس، متن آخر، یا نشانهٔ پیوست MMS. */
@Composable
private fun snippet(thread: ThreadSummary): String = when {
    thread.draft.isNotEmpty() -> stringResource(R.string.draft_prefix, thread.draft)
    thread.snippet.isNotBlank() -> thread.snippet
    thread.attachments > 0 -> stringResource(R.string.snippet_attachment)
    else -> ""
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
