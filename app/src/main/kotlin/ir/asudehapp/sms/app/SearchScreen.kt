package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * جستجو در همهٔ پیامک‌ها، از هر سه پوشه (D20). پیامک پنهان هم پیدا می‌شود و
 * پوشه‌اش کنارش نوشته می‌شود: «پنهان، نه پاک» (اصل ۱).
 */
@Composable
fun SearchScreen(model: AsudehViewModel) {
    val query by model.searchQuery.collectAsState()
    val results by model.searchResults.collectAsState()
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
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(results, key = { "${it.kind}-${it.providerId}" }) { message ->
                SearchResult(message) { model.openSearchResult(message) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SearchResult(message: MessageEntity, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
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
