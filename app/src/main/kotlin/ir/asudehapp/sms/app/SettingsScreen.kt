package ir.asudehapp.sms.app

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.BackupFormat
import ir.asudehapp.sms.data.DigestFrequency
import ir.asudehapp.sms.data.SenderRuleEntity
import ir.asudehapp.sms.data.SenderRuleKind
import ir.asudehapp.sms.data.ThemeMode
import ir.asudehapp.sms.persian.JalaliDate
import java.util.Calendar

/**
 * تنظیمات (D52): صفحهٔ ساده (خلاصه، ارقام، تم، قواعد من، پشتیبان، درباره) و
 * بخش «پیشرفته».
 */
@Composable
fun SettingsScreen(model: AsudehViewModel, isDefaultApp: Boolean) {
    val trashCount by model.trashCount.collectAsState()
    val settings by model.settings.collectAsState()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
    ) { uri -> uri?.let(model::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { model.importBackup(it, isDefaultApp) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Section(stringResource(R.string.settings_digest), first = true)
        for ((value, label) in listOf(
            DigestFrequency.DAILY to R.string.digest_daily,
            DigestFrequency.WEEKLY to R.string.digest_weekly,
            DigestFrequency.OFF to R.string.digest_off,
        )) {
            Choice(stringResource(label), settings.digest == value) { model.setDigest(value) }
        }

        Section(stringResource(R.string.settings_theme))
        for ((value, label) in listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )) {
            Choice(stringResource(label), settings.theme == value) { model.setTheme(value) }
        }
        Toggle(
            stringResource(R.string.settings_digits),
            stringResource(R.string.settings_digits_hint),
            settings.persianDigits,
            model::setPersianDigits,
        )

        Section(stringResource(R.string.settings_rules))
        Link(stringResource(R.string.settings_rules), stringResource(R.string.settings_rules_hint)) {
            model.navigate(Destination.Rules)
        }
        // «حذف‌شده‌ها» (ADR-0010): تنها جایی که پیامک حذف‌شده هنوز هست.
        Link(stringResource(R.string.trash), Texts.count(R.plurals.trash_count, trashCount)) {
            model.navigate(Destination.Trash)
        }

        Section(stringResource(R.string.settings_sending))
        Toggle(
            stringResource(R.string.settings_delivery),
            stringResource(R.string.settings_delivery_hint),
            settings.deliveryReports,
            model::setDeliveryReports,
        )

        Section(stringResource(R.string.settings_backup))
        Text(
            stringResource(R.string.backup_hint),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { exportLauncher.launch(backupFileName()) }) {
                Text(stringResource(R.string.backup_export))
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.backup_import))
            }
        }

        Section(stringResource(R.string.settings_advanced))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toggle(stringResource(R.string.settings_dynamic_color), null, settings.dynamicColor, model::setDynamicColor)
        }
        Toggle(
            stringResource(R.string.settings_show_reason),
            null,
            settings.showReasonEverywhere,
            model::setShowReasonEverywhere,
        )

        Section(stringResource(R.string.settings_about))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                Texts.digits(stringResource(R.string.about_version, BuildConfigInfo.versionName(context))),
                style = MaterialTheme.typography.bodyMedium,
            )
            // نسخهٔ `RulePack` در صفحهٔ «درباره» دیده می‌شود (D33).
            Text(
                Texts.digits(stringResource(R.string.about_rules, model.rulesVersion)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.about_promise),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** نام پیشنهادی فایل پشتیبان، با تاریخ شمسی. */
private fun backupFileName(): String {
    val now = Calendar.getInstance()
    val date = JalaliDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    return "asudeh-%04d-%02d-%02d.%s".format(date.year, date.month, date.day, BackupFormat.EXTENSION)
}

@Composable
private fun Section(title: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Link(label: String, hint: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * «قواعد من» (D45، اصل ۵): هر `Rescue` و `Block` اینجا دیده می‌شود و با یک لمس
 * برگشت‌پذیر است.
 */
@Composable
fun RulesScreen(model: AsudehViewModel) {
    val rules by model.senderRules.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.rules_empty), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(rules, key = { it.address }) { rule ->
            RuleRow(rule) { model.forgetRule(rule.address) }
            HorizontalDivider()
        }
    }
}

@Composable
private fun RuleRow(rule: SenderRuleEntity, onForget: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Texts.sender(rule.address), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (rule.kind == SenderRuleKind.ALLOW) R.string.rule_allow else R.string.rule_block),
                style = MaterialTheme.typography.bodySmall,
                color = if (rule.kind == SenderRuleKind.ALLOW) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        TextButton(onClick = onForget) { Text(stringResource(R.string.rule_forget)) }
    }
}
