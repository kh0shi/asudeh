package ir.asudehapp.sms.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                                Destination.Archive -> stringResource(R.string.archive)
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
                            Destination.Home -> HomeActions(model)
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
        // فاصلهٔ نوارهای سیستم را همین‌جا مصرف می‌کنیم تا صفحهٔ گفتگو بتواند با
        // `imePadding` فقط به اندازهٔ کیبورد بالا بیاید، نه کیبورد به‌علاوهٔ
        // نوار ناوبری.
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (val current = destination) {
                Destination.Home -> HomeScreen(
                    model = model,
                    isDefaultApp = isDefaultApp,
                    onBecomeDefault = onBecomeDefault,
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
                Destination.Archive -> ArchiveScreen(
                    model = model,
                    isDefaultApp = isDefaultApp,
                    onOpenThread = { threadId, folder -> model.navigate(Destination.Conversation(threadId, folder)) },
                )
            }
        }
    }

    RescueFollowUpDialog(model)
    EmptyFolderDialog(model)
    DeleteDialog(model)
    SpamDialog(model)
    EmptyTrashDialog(model)
    ScheduleDialog(model)
    SweepOfferDialog(model)
    SuggestionSampleDialog(model)
    CrashReportDialog(model)
}

/**
 * نوار بالای صفحهٔ اصلی: جستجو، و منوی سه‌نقطه.
 *
 * پوشه‌های «تبلیغات» و «کلاهبرداری» و «تنظیمات» در همین منو هستند، نه بالای
 * فهرست پیامک‌ها و نه یک آیکون جدا: هر سه هر روز جلوی چشم بودند بی‌آنکه هر
 * روز به کارشان بیاید (D42 الف). جستجو آیکون خودش را نگه می‌دارد، چون تنها
 * راه رسیدن به یک پیامک قدیمی است. شمار خوانده‌نشده‌ها کنار نام پوشه‌ها
 * می‌آید تا چیزی پنهان نماند.
 */
@Composable
private fun HomeActions(model: AsudehViewModel) {
    val promoUnread by model.promoUnread.collectAsState()
    val scamCount by model.scamCount.collectAsState()
    var menu by remember { mutableStateOf(false) }

    IconButton(onClick = { model.navigate(Destination.Search) }) {
        Icon(Icons.Default.Search, stringResource(R.string.search))
    }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        FolderMenuItem(
            title = stringResource(R.string.folder_promo),
            trailing = if (promoUnread > 0) Texts.count(R.plurals.new_count, promoUnread) else "",
        ) {
            menu = false
            model.navigate(Destination.FolderView(Folder.PROMO))
        }
        FolderMenuItem(
            title = stringResource(R.string.folder_scam),
            trailing = if (scamCount > 0) Texts.count(R.plurals.new_count, scamCount) else "",
        ) {
            menu = false
            model.navigate(Destination.FolderView(Folder.SCAM))
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.archive)) },
            onClick = {
                menu = false
                model.navigate(Destination.Archive)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            onClick = {
                menu = false
                model.navigate(Destination.Settings)
            },
        )
    }
}

@Composable
private fun FolderMenuItem(title: String, trailing: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        trailingIcon = {
            if (trailing.isNotEmpty()) {
                Text(trailing, style = MaterialTheme.typography.labelMedium)
            }
        },
        onClick = onClick,
    )
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
 * نوار بالا در حالت انتخاب. کارهای پرکاربرد آیکون خودشان را دارند، و **همان‌ها
 * با متن** در منوی سه‌نقطه هم هستند: آیکون تنها همیشه خوانده نمی‌شود، و
 * «اسپم» و «حذف» چیزی نیستند که کاربر باید حدس بزند. حذف از Telephony
 * Provider فقط برای اپ پیش‌فرض ممکن است، پس فقط همان وقت دیده می‌شود.
 */
@Composable
private fun SelectionActions(
    model: AsudehViewModel,
    destination: Destination,
    isDefaultApp: Boolean,
    selectedThreads: Set<Long>,
) {
    if (destination is Destination.Conversation) {
        MessageSelectionActions(model, isDefaultApp)
    } else if (destination == Destination.Archive) {
        ArchiveSelectionActions(model, isDefaultApp, selectedThreads)
    } else {
        ThreadSelectionActions(model, destination, isDefaultApp, selectedThreads)
    }
}

/**
 * انتخاب پیامک در گفتگو: «انتخاب همه»، فوروارد و حذف. پیش از این فقط انتخاب
 * همه و حذف بود و انتخاب چند پیامک تقریباً به کاری نمی‌آمد.
 */
@Composable
private fun MessageSelectionActions(model: AsudehViewModel, isDefaultApp: Boolean) {
    var menu by remember { mutableStateOf(false) }
    IconButton(onClick = model::selectAllMessages) {
        Icon(Icons.Default.Done, stringResource(R.string.select_all))
    }
    IconButton(onClick = model::forwardSelectedMessages) {
        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.forward))
    }
    if (isDefaultApp) {
        IconButton(onClick = model::requestDeleteSelectedMessages) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete))
        }
    }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        ActionMenuItem(stringResource(R.string.select_all), Icons.Default.Done) {
            menu = false
            model.selectAllMessages()
        }
        ActionMenuItem(stringResource(R.string.forward), Icons.AutoMirrored.Filled.Send) {
            menu = false
            model.forwardSelectedMessages()
        }
        if (isDefaultApp) {
            ActionMenuItem(stringResource(R.string.delete), Icons.Default.Delete) {
                menu = false
                model.requestDeleteSelectedMessages()
            }
        }
    }
}

/**
 * انتخاب گفتگو در فهرست: همان کارهایی که پیش از این پشت لمس طولانی پنهان
 * بودند، حالا روی نوار بالا و روی یک یا چند گفتگو با هم. «سنجاق» و «خوانده
 * شد» به حال بیشترِ انتخاب‌شده‌ها نگاه می‌کنند: اگر حتی یکی سنجاق‌نشده باشد،
 * دکمه سنجاق می‌کند.
 *
 * «خوانده شد» آیکون بالا را نگرفته چون باز کردن خود گفتگو هم همین کار را
 * می‌کند؛ در منوی سه‌نقطه هست.
 */
@Composable
private fun ThreadSelectionActions(
    model: AsudehViewModel,
    destination: Destination,
    isDefaultApp: Boolean,
    selectedThreads: Set<Long>,
) {
    val folder = (destination as? Destination.FolderView)?.folder ?: Folder.INBOX
    val threads by when (folder) {
        Folder.PROMO -> model.promoThreads
        Folder.SCAM -> model.scamThreads
        Folder.INBOX -> model.inboxThreads
    }.collectAsState()
    val chosen = threads.filter { it.threadId in selectedThreads }
    val pinning = chosen.any { !it.pinned }
    val muting = chosen.any { !it.muted }
    val marksRead = chosen.any { it.unread > 0 }
    var menu by remember { mutableStateOf(false) }

    val pinLabel = stringResource(if (pinning) R.string.pin else R.string.unpin)
    val muteLabel = stringResource(if (muting) R.string.mute else R.string.unmute)
    val readLabel = stringResource(if (marksRead) R.string.mark_read else R.string.mark_unread)
    val readIcon = if (marksRead) Icons.Default.Email else Icons.Default.MailOutline
    val pin = { model.setPinnedThreads(selectedThreads, pinning) }
    val mute = { model.setMutedThreads(selectedThreads, muting) }
    val markRead = {
        if (marksRead) {
            model.markThreadsRead(selectedThreads, folder)
        } else {
            model.markThreadsUnread(selectedThreads, folder)
        }
    }
    val spam = { model.requestSpamThreads(selectedThreads) }
    val delete = { model.requestDeleteThreads(selectedThreads, folder) }
    val archive = { model.setArchivedThreads(selectedThreads, true) }

    IconButton(onClick = pin) { Icon(Icons.Default.Star, pinLabel) }
    IconButton(onClick = spam) { Icon(Icons.Default.Warning, stringResource(R.string.spam)) }
    if (isDefaultApp) {
        IconButton(onClick = delete) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete_thread))
        }
    }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        ActionMenuItem(stringResource(R.string.select_all), Icons.Default.Done) {
            menu = false
            model.selectAllThreads(threads.map { it.threadId })
        }
        ActionMenuItem(pinLabel, Icons.Default.Star) {
            menu = false
            pin()
        }
        ActionMenuItem(muteLabel, Icons.Default.Notifications) {
            menu = false
            mute()
        }
        ActionMenuItem(readLabel, readIcon) {
            menu = false
            markRead()
        }
        ActionMenuItem(stringResource(R.string.spam), Icons.Default.Warning) {
            menu = false
            spam()
        }
        EmojiMenuItem("📦", stringResource(R.string.archive)) {
            menu = false
            archive()
        }
        if (isDefaultApp) {
            ActionMenuItem(stringResource(R.string.delete_thread), Icons.Default.Delete) {
                menu = false
                delete()
            }
        }
    }
}

/** مثل [ActionMenuItem]، برای کاری که در بستهٔ آیکون‌های core نیست (اصل ۷: حجم اپ). */
@Composable
private fun EmojiMenuItem(emoji: String, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Text(emoji) },
        onClick = onClick,
    )
}

/**
 * انتخاب گفتگو در صفحهٔ «بایگانی» (PARITY §الف). بایگانی به یک پوشه محدود
 * نیست، پس برخلاف [ThreadSelectionActions] هر گفتگو با پوشهٔ خودش خوانده/
 * خوانده‌نشده و حذف می‌شود، نه یک پوشهٔ مشترک.
 */
@Composable
private fun ArchiveSelectionActions(model: AsudehViewModel, isDefaultApp: Boolean, selectedThreads: Set<Long>) {
    val threads by model.archivedThreads.collectAsState()
    val chosen = threads.filter { it.threadId in selectedThreads }
    val marksRead = chosen.any { it.unread > 0 }
    var menu by remember { mutableStateOf(false) }

    val readLabel = stringResource(if (marksRead) R.string.mark_read else R.string.mark_unread)
    val readIcon = if (marksRead) Icons.Default.Email else Icons.Default.MailOutline
    val markRead = {
        for (thread in chosen) {
            if (marksRead) model.markRead(thread.threadId, thread.folder) else model.markUnread(thread.threadId, thread.folder)
        }
        model.clearThreadSelection()
    }
    val unarchive = { model.setArchivedThreads(selectedThreads, false) }
    val delete = { model.requestDeleteArchivedThreads(selectedThreads) }

    val unarchiveLabel = stringResource(R.string.unarchive)
    IconButton(onClick = unarchive) { Text("📦", Modifier.semantics { contentDescription = unarchiveLabel }) }
    if (isDefaultApp) {
        IconButton(onClick = delete) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete_thread))
        }
    }
    IconButton(onClick = { menu = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        ActionMenuItem(stringResource(R.string.select_all), Icons.Default.Done) {
            menu = false
            model.selectAllThreads(threads.map { it.threadId })
        }
        EmojiMenuItem("📦", stringResource(R.string.unarchive)) {
            menu = false
            unarchive()
        }
        ActionMenuItem(readLabel, readIcon) {
            menu = false
            markRead()
        }
        if (isDefaultApp) {
            ActionMenuItem(stringResource(R.string.delete_thread), Icons.Default.Delete) {
                menu = false
                delete()
            }
        }
    }
}

/** یک گزینه در منوی سه‌نقطه: همان آیکون نوار بالا، این بار با متنش. */
@Composable
private fun ActionMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

/**
 * «اسپم» روی گفتگوهای انتخاب‌شده (ADR-0006 بند ۲). یک مرحله، چون چیزی حذف
 * نمی‌شود و قاعده در «قواعد من» برداشتنی است؛ ولی متن می‌گوید دقیقاً چه
 * اتفاقی می‌افتد (اصل ۵).
 */
@Composable
private fun SpamDialog(model: AsudehViewModel) {
    val pending by model.spamRequest.collectAsState()
    val request = pending ?: return

    AlertDialog(
        onDismissRequest = model::cancelSpam,
        title = { Text(stringResource(R.string.spam)) },
        text = {
            Column {
                Text(
                    if (request.senders.isEmpty()) {
                        stringResource(R.string.spam_nothing)
                    } else {
                        Texts.count(R.plurals.spam_body, request.threads)
                    },
                )
                if (request.senders.isNotEmpty()) {
                    Text(
                        stringResource(R.string.spam_explain),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (request.senders.isEmpty()) {
                TextButton(onClick = model::cancelSpam) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(onClick = model::confirmSpam) { Text(stringResource(R.string.spam_confirm)) }
            }
        },
        dismissButton = {
            if (request.senders.isNotEmpty()) {
                TextButton(onClick = model::cancelSpam) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
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
    is UiNotice.MarkedSpam -> Texts.count(R.plurals.notice_spam, notice.senders)
    UiNotice.SpamNothing -> stringResource(R.string.spam_nothing)
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
    onOpenThread: (Long) -> Unit,
) {
    // فهرست کامل فقط برای «خالی است؟» (زیر) لازم است؛ خود ردیف‌ها از نسخهٔ
    // صفحه‌بندی‌شده می‌آیند (توضیح در AsudehViewModel.inboxThreads).
    val threads by model.inboxThreads.collectAsState()
    val lazyThreads = model.pagedInboxThreads.collectAsLazyPagingItems()
    val selectedThreads by model.selectedThreads.collectAsState()
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

        if (threads.isEmpty() && !syncing) {
            item { EmptyState(stringResource(R.string.empty_inbox)) }
        }
        pagedThreadItems(
            items = lazyThreads,
            model = model,
            isDefaultApp = isDefaultApp,
            selectedThreads = selectedThreads,
            folderFor = { Folder.INBOX },
            onOpenThread = { thread -> onOpenThread(thread.threadId) },
        )
    }
}

/**
 * ردیف‌های یک `LazyColumn` از یک `LazyPagingItems`. Paging 3 هنگام
 * بارگذاری، `null` برمی‌گرداند (placeholder)؛ چون ارتفاع [ThreadRow] با
 * محتوایش کمی فرق می‌کند و از پرش صفحه در پیمایش سریع جلوگیری می‌کند، به‌جای
 * حذف کامل ردیف یک جای خالی هم‌ارتفاع نشان می‌دهیم، نه کرش.
 */
private fun LazyListScope.pagedThreadItems(
    items: LazyPagingItems<ThreadSummary>,
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    selectedThreads: Set<Long>,
    folderFor: (ThreadSummary) -> Folder,
    onOpenThread: (ThreadSummary) -> Unit,
) {
    items(items.itemCount, key = items.itemKey { it.threadId }) { index ->
        val thread = items[index]
        if (thread == null) {
            Box(Modifier.fillMaxWidth().height(72.dp))
        } else {
            ThreadRow(
                model = model,
                thread = thread,
                folder = folderFor(thread),
                isDefaultApp = isDefaultApp,
                selected = thread.threadId in selectedThreads,
                selectionMode = selectedThreads.isNotEmpty(),
                onClick = { onOpenThread(thread) },
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
    val lazyThreads = model.pagedThreadsIn(folder).collectAsLazyPagingItems()
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
            pagedThreadItems(
                items = lazyThreads,
                model = model,
                isDefaultApp = isDefaultApp,
                selectedThreads = selectedThreads,
                folderFor = { folder },
                onOpenThread = { thread -> onOpenThread(thread.threadId) },
            )
        }
    }
}

/**
 * «بایگانی» (PARITY §الف): گفتگوهایی که کاربر از فهرست پوشه‌اش برداشته، از
 * هر پوشه‌ای که باشند. باز کردن گفتگو به همان پوشه‌ای می‌رود که آخرین
 * پیامکش در آن است، چون بایگانی خودش پوشه نیست.
 */
@Composable
private fun ArchiveScreen(
    model: AsudehViewModel,
    isDefaultApp: Boolean,
    onOpenThread: (Long, Folder) -> Unit,
) {
    val threads by model.archivedThreads.collectAsState()
    val lazyThreads = model.pagedArchivedThreads.collectAsLazyPagingItems()
    val selectedThreads by model.selectedThreads.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (threads.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_archive)) }
        }
        pagedThreadItems(
            items = lazyThreads,
            model = model,
            isDefaultApp = isDefaultApp,
            selectedThreads = selectedThreads,
            folderFor = { it.folder },
            onOpenThread = { thread -> onOpenThread(thread.threadId, thread.folder) },
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
            Text(stringResource(R.string.not_default_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.not_default_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onBecomeDefault) { Text(stringResource(R.string.become_default)) }
        }
    }
}

/**
 * یک گفتگو در فهرست، با کشیدن انگشت هم. کشیدن به یک طرف علامت خوانده/
 * خوانده‌نشده را عوض می‌کند؛ کشیدن به طرف دیگر حذف را می‌پرسد (فقط اپ
 * پیش‌فرض)، با همان `DeleteDialog` که دکمهٔ حذف نوار انتخاب هم استفاده
 * می‌کند. هیچ‌کدام کارت را واقعاً از صفحه برنمی‌دارد؛ کارت به جای خودش
 * برمی‌گردد و کار از راه ViewModel انجام می‌شود، چون حذف به تأیید نیاز دارد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadRow(
    model: AsudehViewModel,
    thread: ThreadSummary,
    folder: Folder,
    isDefaultApp: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    if (selectionMode) {
        ThreadRowContent(model, thread, selected, selectionMode, onClick)
        return
    }
    val marksRead = thread.unread > 0
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (marksRead) {
                        model.markThreadsRead(setOf(thread.threadId), folder)
                    } else {
                        model.markThreadsUnread(setOf(thread.threadId), folder)
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isDefaultApp) model.requestDeleteThreads(setOf(thread.threadId), folder)
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = isDefaultApp,
        backgroundContent = { SwipeBackground(dismissState.targetValue, marksRead) },
    ) {
        ThreadRowContent(model, thread, selected, selectionMode, onClick)
    }
}

/** پس‌زمینهٔ کشیدن انگشت: سمتی که به آن کشیده می‌شود، رنگ و آیکونش را نشان می‌دهد. */
@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue, marksRead: Boolean) {
    val (color, icon, alignment) = when (target) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            if (marksRead) Icons.Default.Email else Icons.Default.MailOutline,
            Alignment.CenterStart,
        )
        SwipeToDismissBoxValue.EndToStart -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Delete,
            Alignment.CenterEnd,
        )
        SwipeToDismissBoxValue.Settled -> Triple(Color.Unspecified, null, Alignment.Center)
    }
    Box(
        Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        if (icon != null) Icon(icon, null)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRowContent(
    model: AsudehViewModel,
    thread: ThreadSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Unspecified,
            )
            .combinedClickable(
                onClick = { if (selectionMode) model.toggleThread(thread.threadId) else onClick() },
                onLongClick = { model.toggleThread(thread.threadId) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { model.toggleThread(thread.threadId) })
        } else {
            val primaryAddress = thread.recipients
                .split(MessageEntity.RECIPIENT_SEPARATOR)
                .firstOrNull { it.isNotEmpty() }
                ?: thread.address
            ThreadAvatar(primaryAddress, Modifier.padding(end = 12.dp))
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
                if (thread.muted) {
                    Icon(
                        Icons.Default.Notifications,
                        stringResource(R.string.muted),
                        Modifier.size(16.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
