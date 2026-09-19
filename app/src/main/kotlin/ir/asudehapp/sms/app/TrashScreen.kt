package ir.asudehapp.sms.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R

/**
 * «حذف‌شده‌ها» (ADR-0010). اینجا تنها نسخهٔ باقی‌ماندهٔ پیامک‌های حذف‌شده است:
 * از Telephony Provider سیستم برداشته شده‌اند و تا وقتی کاربر این صفحه را
 * خالی نکند، برگشتنی‌اند. اپ هرگز خودش آن را خالی نمی‌کند.
 */
@Composable
fun TrashScreen(model: AsudehViewModel) {
    val messages by model.trash.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.trash_hint),
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (messages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Texts.count(R.plurals.trash_count, messages.size),
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = model::requestEmptyTrash) {
                    Text(stringResource(R.string.empty_trash))
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.trash_empty_state), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Texts.sender(message.address),
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
                    if (message.restorable) {
                        TextButton(onClick = { model.restoreFromTrash(listOf(message.id)) }) {
                            Text(stringResource(R.string.trash_restore))
                        }
                    } else {
                        Text(
                            stringResource(R.string.trash_not_restorable),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
