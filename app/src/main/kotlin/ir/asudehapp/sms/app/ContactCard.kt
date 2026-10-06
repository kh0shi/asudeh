package ir.asudehapp.sms.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.SenderRuleKind
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.telephony.Contacts

/**
 * کارت مخاطب، با لمس نام در سربرگ گفتگو (ROADMAP E8): عکس بزرگ، شماره‌ها و
 * «قواعد این فرستنده». قاعده‌ها همان‌هایی‌اند که در «قواعد من» دیده می‌شوند و
 * هر دو کار برگشت‌پذیرند (اصل ۵)؛ هیچ پیامکی پاک نمی‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCard(model: AsudehViewModel, state: ConversationUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (address in state.participants) {
                ContactHeader(address)
            }
            if (!state.isGroup && state.address.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SenderRules(model, state.address) {
                    onDismiss()
                    model.navigate(Destination.Rules)
                }
            }
        }
    }
}

@Composable
private fun ContactHeader(address: String) {
    val context = LocalContext.current
    var numbers by remember(address) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(address) { numbers = Contacts.numbersOf(context, address) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ThreadAvatar(address, Modifier.padding(bottom = 8.dp), size = 72.dp)
        Text(Texts.participants(listOf(address)), style = MaterialTheme.typography.titleLarge)
        for (number in numbers.ifEmpty { listOf(address) }) {
            Text(
                // شماره همیشه چپ‌به‌راست، حتی در رابط فارسی (`+98…`).
                "\u2066" + Texts.digits(number) + "\u2069",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderRules(model: AsudehViewModel, address: String, onAllRules: () -> Unit) {
    val rules by model.senderRules.collectAsState()
    val normalized = remember(address) { Addresses.normalize(address) }
    val kind = rules.firstOrNull { it.address == normalized }?.kind
    Text(stringResource(R.string.contact_rules), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(
            when (kind) {
                SenderRuleKind.ALLOW -> R.string.contact_rule_allowed
                SenderRuleKind.BLOCK -> R.string.contact_rule_blocked
                null -> R.string.contact_rule_none
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kind != SenderRuleKind.ALLOW) {
            OutlinedButton(onClick = { model.allowSender(address) }) {
                Text(stringResource(R.string.contact_rule_allow))
            }
        }
        if (kind != SenderRuleKind.BLOCK) {
            OutlinedButton(onClick = { model.block(address) }) {
                Text(stringResource(R.string.contact_rule_block))
            }
        }
        if (kind != null) {
            OutlinedButton(onClick = { model.forgetRule(address) }) {
                Text(stringResource(R.string.contact_rule_forget))
            }
        }
    }
    TextButton(onClick = onAllRules) { Text(stringResource(R.string.settings_rules)) }
}
