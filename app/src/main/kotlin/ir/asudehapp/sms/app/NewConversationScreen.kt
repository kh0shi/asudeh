package ir.asudehapp.sms.app

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.persian.PersianText
import ir.asudehapp.sms.persian.SearchText
import ir.asudehapp.sms.telephony.ContactPhone
import ir.asudehapp.sms.telephony.Contacts

/**
 * گفتگوی تازه: یک یا چند شماره، با فهرست مخاطب‌ها همان‌جا زیر کادر. تایپ کردن
 * فهرست را صاف می‌کند: هر چه بعد از آخرین ویرگول نوشته شود با نام و شمارهٔ
 * مخاطب‌ها سنجیده می‌شود، و لمس یک مخاطب همان تکه را با شماره‌اش عوض می‌کند.
 * پس هم می‌شود شماره را نوشت و هم از فهرست برداشت.
 *
 * بدون مجوز مخاطب‌ها فهرست خالی می‌ماند و انتخابگر خود اندروید سر جایش است، که
 * به هیچ مجوزی نیاز ندارد: اپ بدون این مجوز هم کار می‌کند.
 *
 * اگر متنی منتظر گیرنده باشد — فوروارد یک پیامک، یا اشتراک‌گذاری از اپ دیگر —
 * همین‌جا دیده می‌شود، تا کاربر نداند چه چیزی در راه است پیش نرود.
 */
@Composable
fun NewConversationScreen(model: AsudehViewModel) {
    var numbers by rememberSaveable { mutableStateOf("") }
    val pending by model.pendingShare.collectAsState()
    val contacts by model.contacts.collectAsState()
    val context = LocalContext.current
    var canReadContacts by remember { mutableStateOf(Contacts.canRead(context)) }
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canReadContacts = granted
        if (granted) model.refreshContacts()
    }
    LaunchedEffect(canReadContacts) { if (canReadContacts) model.refreshContacts() }

    val pickPhone = rememberLauncherForActivityResult(PickPhone) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val number = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        numbers = withRecipient(numbers, number)
    }
    val recipients = parseRecipients(numbers)
    fun start() {
        if (recipients.isNotEmpty()) model.startConversation(recipients)
    }

    val shown = remember(contacts, numbers) { filterContacts(contacts, numbers) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = numbers,
            onValueChange = { numbers = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.new_conversation_hint)) },
            // شماره چپ‌به‌راست است، حتی وسط یک رابط راست‌به‌چپ. بدون این،
            // «+۹۸» و ویرگول جدا‌کننده جابه‌جا دیده می‌شدند و شماره‌ها به هم
            // می‌ریختند (D50). کیبورد متنی است تا نام مخاطب هم نوشتنی باشد.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { start() }),
        )
        pending?.let { share ->
            val text = share.text.orEmpty()
            if (text.isNotEmpty()) {
                Text(stringResource(R.string.pending_share), style = MaterialTheme.typography.bodySmall)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (share.image != null) {
                Text(
                    stringResource(R.string.pending_share_image),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (recipients.size > 1) {
            Text(stringResource(R.string.new_conversation_group_hint), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = ::start, enabled = recipients.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_conversation))
        }
        HorizontalDivider()
        when {
            !canReadContacts -> {
                Text(
                    stringResource(R.string.contacts_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text(stringResource(R.string.contacts_show))
                }
                TextButton(onClick = { pickPhone.launch(Unit) }) {
                    Text(stringResource(R.string.pick_contact))
                }
            }

            shown.isEmpty() -> {
                Text(
                    stringResource(
                        if (contacts.isEmpty()) R.string.contacts_empty else R.string.contacts_no_match,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { pickPhone.launch(Unit) }) {
                    Text(stringResource(R.string.pick_contact))
                }
            }

            // فهرست بقیهٔ صفحه را می‌گیرد و خودش می‌چرخد؛ کادر شماره و دکمه‌ها
            // سر جایشان می‌مانند.
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(shown, key = { it.number }) { contact ->
                    ContactRow(contact) { numbers = withRecipient(numbers, contact.number) }
                }
            }
        }
    }
}

/** یک مخاطب در فهرست: نام بالا، شماره زیرش و همیشه چپ‌به‌راست (D50). */
@Composable
private fun ContactRow(contact: ContactPhone, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        if (contact.name.isNotBlank()) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            contact.number,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * فهرست، بر اساس آنچه بعد از آخرین ویرگول نوشته شده. نام با
 * [SearchText.tokens] سنجیده می‌شود، پس «ی/ي»، نیم‌فاصله و ارقام فارسی فرقی
 * نمی‌کنند؛ شماره فقط با رقم‌هایش، تا «۰۹۱۲» و «+۹۸۹۱۲» هم را پیدا کنند.
 */
private fun filterContacts(contacts: List<ContactPhone>, numbers: String): List<ContactPhone> {
    val term = lastSegment(numbers)
    if (term.isBlank()) return contacts
    val words = SearchText.tokens(term)
    val digits = PersianText.latinDigits(term).filter(Char::isDigit)
    return contacts.filter { contact ->
        val name = SearchText.tokens(contact.name).joinToString(" ")
        val number = contact.number.filter(Char::isDigit)
        words.all { name.contains(it) } || (digits.isNotEmpty() && number.contains(digits))
    }
}

/** آنچه کاربر همین حالا دارد می‌نویسد: تکهٔ بعد از آخرین جداکننده. */
private fun lastSegment(numbers: String): String =
    numbers.takeLastWhile { it !in SEPARATORS }.trim()

/** جای همان تکه را با شمارهٔ مخاطب پر می‌کند و برای گیرندهٔ بعدی جا باز می‌کند. */
private fun withRecipient(numbers: String, number: String): String {
    val kept = numbers.dropLastWhile { it !in SEPARATORS }
    return (kept + number).trim() + ", "
}

/** شماره‌ها جدا با ویرگول فارسی یا لاتین یا نقطه‌ویرگول. */
private val SEPARATORS = setOf(',', '،', ';', '؛', '\n')

/**
 * ارقام فارسی لاتین می‌شوند. تکه‌ای که هیچ رقمی ندارد گیرنده نیست: نامی است که
 * کاربر دارد برای صاف کردن فهرست مخاطب‌ها می‌نویسد.
 */
private fun parseRecipients(text: String): List<String> =
    PersianText.latinDigits(text)
        .split(',', '،', ';', '؛', '\n')
        .map { it.trim() }
        .filter { it.any(Char::isDigit) }

/** انتخابگر شمارهٔ تلفن اندروید؛ خروجی نشانی دادهٔ همان شماره است. */
private object PickPhone : ActivityResultContract<Unit, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): android.net.Uri? =
        intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}
