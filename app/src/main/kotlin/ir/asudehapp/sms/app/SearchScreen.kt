package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.SearchKind
import ir.asudehapp.sms.model.SearchScope

/**
 * جستجو در همهٔ پیامک‌ها، از هر سه پوشه (D20). پیامک پنهان هم پیدا می‌شود و
 * پوشه‌اش کنارش نوشته می‌شود: «پنهان، نه پاک» (اصل ۱).
 */
@Composable
fun SearchScreen(model: AsudehViewModel) {
    val query by model.searchQuery.collectAsState()
    val results by model.searchResults.collectAsState()
    val scope by model.searchScope.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = model::updateSearch,
            modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        SearchScopeChips(scope, model)
        LazyColumn(Modifier.fillMaxSize()) {
            if ((query.isNotBlank() || scope.narrowed) && results.isEmpty()) {
                item { EmptyState(stringResource(R.string.search_empty)) }
            }
            items(results, key = { "${it.kind}-${it.providerId}" }) { message ->
                SearchResult(message) { model.openSearchResult(message) }
                HorizontalDivider()
            }
        }
    }
}

/**
 * فیلتر نتیجه (ROADMAP E6): نوع پیامک، «یک فرستنده» و «همین گفتگو». هیچ‌کدام
 * وارد عبارت FTS نمی‌شوند (`SearchScope`).
 */
@Composable
private fun SearchScopeChips(scope: SearchScope, model: AsudehViewModel) {
    var askSender by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scope.threadId > 0) {
            FilterChip(
                selected = true,
                onClick = model::clearSearchThread,
                label = { Text(stringResource(R.string.search_this_thread)) },
                trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.cancel), Modifier.size(16.dp)) },
            )
        }
        for ((kind, label) in listOf(
            SearchKind.ATTACHMENTS to R.string.search_kind_attachments,
            SearchKind.OTP to R.string.search_kind_otp,
            SearchKind.BANK to R.string.search_kind_bank,
            SearchKind.HIDDEN to R.string.search_kind_hidden,
        )) {
            FilterChip(
                selected = scope.kind == kind,
                onClick = { model.setSearchKind(if (scope.kind == kind) SearchKind.ALL else kind) },
                label = { Text(stringResource(label)) },
            )
        }
        FilterChip(
            selected = scope.sender.isNotEmpty(),
            onClick = { if (scope.sender.isNotEmpty()) model.setSearchSender("") else askSender = true },
            label = {
                Text(
                    if (scope.sender.isNotEmpty()) {
                        Texts.sender(scope.sender)
                    } else {
                        stringResource(R.string.search_one_sender)
                    },
                )
            },
        )
    }
    if (askSender) {
        SenderDialog(onDismiss = { askSender = false }) { sender ->
            askSender = false
            model.setSearchSender(sender)
        }
    }
}

@Composable
private fun SenderDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_one_sender)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.search_sender_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * پیامک‌های نشان‌دار از همهٔ گفتگوها، تازه‌ترین نشان اول (D6). هر ردیف همان
 * ردیف جستجوست و به گفتگوی خودش می‌برد.
 */
@Composable
fun StarredScreen(model: AsudehViewModel) {
    val starred by model.starredMessages.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        if (starred.isEmpty()) {
            item { EmptyState(stringResource(R.string.starred_empty)) }
        }
        items(starred, key = { "${it.kind}-${it.providerId}" }) { message ->
            SearchResult(message) { model.openSearchResult(message) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResult(message: MessageEntity, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (message.isGroup) Texts.participants(message.participants) else Texts.sender(message.address),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Texts.timestamp(message.dateReceived), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            message.body,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (message.folder != Folder.INBOX) {
            Text(
                stringResource(R.string.search_in_folder, Texts.folderName(message.folder)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
