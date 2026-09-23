package ir.asudehapp.sms.app

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.data.AppLanguage
import ir.asudehapp.sms.data.BackupFormat
import ir.asudehapp.sms.data.DateStyle
import ir.asudehapp.sms.data.DigestFrequency
import ir.asudehapp.sms.data.KeywordRuleEntity
import ir.asudehapp.sms.data.SenderRuleEntity
import ir.asudehapp.sms.data.SenderRuleKind
import ir.asudehapp.sms.data.ThemeMode
import ir.asudehapp.sms.model.DefaultRule
import ir.asudehapp.sms.model.DefaultRules
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.ui.LocalUiIsPersian
import java.util.Calendar

/**
 * تنظیمات (D52). ترتیب بخش‌ها از روی «هر چند وقت یک بار عوض می‌شود» است:
 * نمایش و ارسال بالا، و هر چیزی که به تبلیغ و پیامک پنهان مربوط است یک‌جا در
 * پایین. پیش از این «خلاصهٔ پیامک‌های پنهان» اولین چیز صفحه بود، که نه
 * پرکاربردترین بود و نه به تنهایی معنی می‌داد.
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
        Section(stringResource(R.string.settings_language), first = true)
        for ((value, label) in listOf(
            AppLanguage.SYSTEM to R.string.language_system,
            AppLanguage.FA to R.string.language_fa,
            AppLanguage.EN to R.string.language_en,
        )) {
            Choice(stringResource(label), settings.language == value) { model.setLanguage(value) }
        }

        Section(stringResource(R.string.settings_display))
        for ((value, label) in listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )) {
            Choice(stringResource(label), settings.theme == value) { model.setTheme(value) }
        }
        Section(stringResource(R.string.settings_date))
        for ((value, label) in listOf(
            DateStyle.AUTO to R.string.date_auto,
            DateStyle.JALALI to R.string.date_jalali,
            DateStyle.GREGORIAN to R.string.date_gregorian,
        )) {
            Choice(stringResource(label), settings.dateStyle == value) { model.setDateStyle(value) }
        }
        // ارقام فارسی فقط در رابط فارسی معنی دارد؛ در انگلیسی گزینه‌اش نیست.
        if (LocalUiIsPersian.current) {
            Toggle(
                stringResource(R.string.settings_digits),
                stringResource(R.string.settings_digits_hint),
                settings.persianDigits,
                model::setPersianDigits,
            )
        }
        Toggle(
            stringResource(R.string.settings_message_clock),
            stringResource(R.string.settings_message_clock_hint),
            settings.showMessageClock,
            model::setShowMessageClock,
        )

        Section(stringResource(R.string.settings_sending))
        Toggle(
            stringResource(R.string.settings_delivery),
            stringResource(R.string.settings_delivery_hint),
            settings.deliveryReports,
            model::setDeliveryReports,
        )

        // پیام چندرسانه‌ای همیشه ذخیره می‌شود؛ این‌ها فقط دریافت خودکار و
        // فرستادن را خاموش می‌کنند، پس هیچ پیامی گم نمی‌شود.
        Section(stringResource(R.string.settings_mms))
        Toggle(
            stringResource(R.string.settings_mms_auto),
            stringResource(R.string.settings_mms_auto_hint),
            settings.mmsAutoDownload,
            model::setMmsAutoDownload,
        )
        Toggle(
            stringResource(R.string.settings_mms_sending),
            stringResource(R.string.settings_mms_sending_hint),
            settings.mmsSending,
            model::setMmsSending,
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
        // «حذف‌شده‌ها» (ADR-0010): تنها جایی که پیامک حذف‌شده هنوز هست.
        Link(stringResource(R.string.trash), Texts.count(R.plurals.trash_count, trashCount)) {
            model.navigate(Destination.Trash)
        }

        // هر چیزی که به تبلیغ و پیامک پنهان مربوط است، یک‌جا و در پایین صفحه.
        Section(stringResource(R.string.settings_spam))
        Link(stringResource(R.string.settings_rules), stringResource(R.string.settings_rules_hint)) {
            model.navigate(Destination.Rules)
        }
        Text(
            stringResource(R.string.settings_digest),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        for ((value, label) in listOf(
            DigestFrequency.DAILY to R.string.digest_daily,
            DigestFrequency.WEEKLY to R.string.digest_weekly,
            DigestFrequency.OFF to R.string.digest_off,
        )) {
            Choice(stringResource(label), settings.digest == value) { model.setDigest(value) }
        }
        Toggle(
            stringResource(R.string.settings_show_reason),
            null,
            settings.showReasonEverywhere,
            model::setShowReasonEverywhere,
        )

        Section(stringResource(R.string.settings_advanced))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toggle(stringResource(R.string.settings_dynamic_color), null, settings.dynamicColor, model::setDynamicColor)
        }

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
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
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
 * «قواعد من» (D45، اصل ۵، ADR-0012).
 *
 * سه چیز اینجاست: قاعده‌هایی که کاربر خودش با «این تبلیغ نیست» و «همیشه تبلیغ»
 * ساخته، قاعده‌هایی که همین‌جا دستی اضافه می‌کند (شماره یا کلیدواژه)، و
 * قاعده‌های پیش‌فرض خود آسوده که تا امروز نادیدنی بودند. همه با یک لمس
 * برگشت‌پذیرند.
 */
@Composable
fun RulesScreen(model: AsudehViewModel) {
    val senderRules by model.senderRules.collectAsState()
    val keywordRules by model.keywordRules.collectAsState()
    val settings by model.settings.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.rules_add)) }
            }
        }

        item { Section(stringResource(R.string.rules_mine), first = true) }
        if (senderRules.isEmpty() && keywordRules.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.rules_empty),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(senderRules, key = { "sender-" + it.address }) { rule ->
            SenderRuleRow(rule) { model.forgetRule(rule.address) }
            HorizontalDivider()
        }
        items(keywordRules, key = { "keyword-" + it.normalized }) { rule ->
            KeywordRuleRow(rule) { model.forgetKeywordRule(rule.normalized) }
            HorizontalDivider()
        }

        item { Section(stringResource(R.string.rules_defaults)) }
        item {
            Text(
                stringResource(R.string.rules_defaults_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(DefaultRules.ALLOW_KEYWORDS, key = { "default-allow-" + it }) { keyword ->
            DefaultKeywordRow(
                keyword = keyword,
                kind = SenderRuleKind.ALLOW,
                id = DefaultRules.allowKeywordId(keyword),
                settings = settings,
                model = model,
            )
        }
        items(DefaultRules.BLOCK_KEYWORDS, key = { "default-block-" + it }) { keyword ->
            DefaultKeywordRow(
                keyword = keyword,
                kind = SenderRuleKind.BLOCK,
                id = DefaultRules.blockKeywordId(keyword),
                settings = settings,
                model = model,
            )
        }
        items(DefaultRule.entries.toList(), key = { "default-" + it.name }) { rule ->
            Toggle(
                label = stringResource(defaultRuleLabel(rule)),
                hint = stringResource(defaultRuleHint(rule)),
                checked = rule.name !in settings.disabledDefaultRules,
                onChange = { model.setDefaultRuleEnabled(rule, it) },
            )
        }
    }

    if (adding) {
        AddRuleDialog(
            onDismiss = { adding = false },
            onAdd = { value, kind, isKeyword ->
                adding = false
                if (isKeyword) model.addKeywordRule(value, kind) else model.addSenderRule(value, kind)
            },
        )
    }
}

@Composable
private fun SenderRuleRow(rule: SenderRuleEntity, onForget: () -> Unit) {
    RuleRow(
        title = Texts.sender(rule.address),
        subtitle = stringResource(
            if (rule.kind == SenderRuleKind.ALLOW) R.string.rule_allow else R.string.rule_block,
        ),
        allow = rule.kind == SenderRuleKind.ALLOW,
        onForget = onForget,
    )
}

@Composable
private fun KeywordRuleRow(rule: KeywordRuleEntity, onForget: () -> Unit) {
    RuleRow(
        title = "«${rule.keyword}»",
        subtitle = stringResource(
            if (rule.kind == SenderRuleKind.ALLOW) {
                R.string.rule_keyword_allow
            } else {
                R.string.rule_keyword_block
            },
        ),
        allow = rule.kind == SenderRuleKind.ALLOW,
        onForget = onForget,
    )
}

@Composable
private fun RuleRow(title: String, subtitle: String, allow: Boolean, onForget: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (allow) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        TextButton(onClick = onForget) { Text(stringResource(R.string.rule_forget)) }
    }
}

/**
 * یک کلیدواژهٔ پیش‌فرض. پاک نمی‌شود، چون مال آسوده است و با به‌روزرسانی ممکن
 * است عوض شود؛ ولی خاموش می‌شود و خاموشی‌اش می‌ماند.
 */
@Composable
private fun DefaultKeywordRow(
    keyword: String,
    kind: SenderRuleKind,
    id: String,
    settings: SettingsState,
    model: AsudehViewModel,
) {
    Toggle(
        label = "«$keyword»",
        hint = stringResource(
            if (kind == SenderRuleKind.ALLOW) R.string.rule_keyword_allow else R.string.rule_keyword_block,
        ),
        checked = id !in settings.disabledDefaultKeywords,
        onChange = { model.setDefaultKeywordEnabled(id, it) },
    )
}

private fun defaultRuleLabel(rule: DefaultRule): Int = when (rule) {
    DefaultRule.CONTACTS_IN_ALLOWLIST -> R.string.default_rule_contacts
    DefaultRule.MOBILE_NEVER_HIDDEN -> R.string.default_rule_mobile
    DefaultRule.OTP_AND_BANK_ALWAYS_INBOX -> R.string.default_rule_otp
    DefaultRule.HIDE_PROMO -> R.string.default_rule_promo
    DefaultRule.HIDE_SCAM -> R.string.default_rule_scam
}

private fun defaultRuleHint(rule: DefaultRule): Int = when (rule) {
    DefaultRule.CONTACTS_IN_ALLOWLIST -> R.string.default_rule_contacts_hint
    DefaultRule.MOBILE_NEVER_HIDDEN -> R.string.default_rule_mobile_hint
    DefaultRule.OTP_AND_BANK_ALWAYS_INBOX -> R.string.default_rule_otp_hint
    DefaultRule.HIDE_PROMO -> R.string.default_rule_promo_hint
    DefaultRule.HIDE_SCAM -> R.string.default_rule_scam_hint
}

/** افزودن دستی یک قاعده: شماره یا کلیدواژه، در فهرست سفید یا سیاه. */
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (value: String, kind: SenderRuleKind, isKeyword: Boolean) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    var isKeyword by rememberSaveable { mutableStateOf(true) }
    var kind by rememberSaveable { mutableStateOf(SenderRuleKind.BLOCK) }
    val canAdd = remember(value) { value.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Choice(stringResource(R.string.rules_kind_keyword), isKeyword) { isKeyword = true }
                Choice(stringResource(R.string.rules_kind_sender), !isKeyword) { isKeyword = false }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Choice(stringResource(R.string.rules_list_allow), kind == SenderRuleKind.ALLOW) {
                    kind = SenderRuleKind.ALLOW
                }
                Choice(stringResource(R.string.rules_list_block), kind == SenderRuleKind.BLOCK) {
                    kind = SenderRuleKind.BLOCK
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = {
                        Text(
                            stringResource(
                                if (isKeyword) R.string.rules_value_keyword else R.string.rules_value_sender,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isKeyword) KeyboardType.Text else KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (isKeyword) {
                    Text(
                        stringResource(R.string.rules_keyword_hint),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(value, kind, isKeyword) }, enabled = canAdd) {
                Text(stringResource(R.string.rules_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
