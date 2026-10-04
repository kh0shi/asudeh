package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.FolderSender
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Folder

/**
 * بالای پوشهٔ تبلیغات: «همه خوانده شد» و «خالی کردن پوشه» (D45؛ حذف از provider
 * فقط برای اپ پیش‌فرض)، و انتخاب نما (E5).
 */
@Composable
fun PromoHeader(model: AsudehViewModel, isDefaultApp: Boolean, bySender: Boolean, onBySender: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = { model.markFolderRead(Folder.PROMO) }) {
            Text(stringResource(R.string.mark_all_read))
        }
        if (isDefaultApp) {
            TextButton(onClick = { model.requestEmptyFolder(Folder.PROMO) }) {
                Text(stringResource(R.string.empty_folder))
            }
        }
    }
    PromoViewChips(bySender, onBySender)
    HorizontalDivider()
}

/** «گفتگوها» یا «فرستنده‌ها» در پوشهٔ تبلیغات (ROADMAP E5). */
@Composable
private fun PromoViewChips(bySender: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !bySender,
            onClick = { onChange(false) },
            label = { Text(stringResource(R.string.promo_by_thread)) },
        )
        FilterChip(
            selected = bySender,
            onClick = { onChange(true) },
            label = { Text(stringResource(R.string.promo_by_sender)) },
        )
    }
}

/**
 * پوشهٔ تبلیغات، هر فرستنده یک ردیف با شمار پیامک‌هایش (ROADMAP E5). از همان
 * ردیف «لغو ۱۱» (فقط خطوط انبوه، با تأیید صریح D28) و «مسدود» (همان قاعدهٔ
 * `Block` که در «قواعد من» دیده و برداشته می‌شود). لمس ردیف گفتگو را باز می‌کند.
 */
@Composable
fun PromoSenderList(model: AsudehViewModel, onOpenThread: (Long) -> Unit) {
    val senders by model.promoSenders.collectAsState()
    var confirmUnsub by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(senders, key = { it.address }) { sender ->
            SenderRow(
                sender = sender,
                onOpen = { onOpenThread(sender.threadId) },
                onUnsub = { confirmUnsub = sender.address },
                onBlock = { model.block(sender.address) },
            )
            HorizontalDivider()
        }
    }
    confirmUnsub?.let { address ->
        AlertDialog(
            onDismissRequest = { confirmUnsub = null },
            title = { Text(stringResource(R.string.unsub_confirm_title)) },
            text = { Text(stringResource(R.string.unsub_confirm_body, Texts.sender(address))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsub = null
                    model.unsubscribeFrom(address)
                }) { Text(stringResource(R.string.unsub_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsub = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SenderRow(sender: FolderSender, onOpen: () -> Unit, onUnsub: () -> Unit, onBlock: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThreadAvatar(sender.address, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Texts.sender(sender.address),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (sender.unread > 0) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Texts.count(R.plurals.promo_sender_count, sender.total),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (Addresses.isAdLine(sender.address)) {
            TextButton(onClick = onUnsub) { Text(stringResource(R.string.unsub)) }
        }
        TextButton(onClick = onBlock) { Text(stringResource(R.string.promo_block)) }
    }
}
