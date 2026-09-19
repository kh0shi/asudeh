package ir.asudehapp.sms.app

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.persian.PersianText

/**
 * گفتگوی تازه: یک یا چند شماره. انتخاب از مخاطب‌ها با انتخابگر خود اندروید
 * است و به هیچ مجوز تازه‌ای نیاز ندارد.
 */
@Composable
fun NewConversationScreen(model: AsudehViewModel) {
    var numbers by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
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
        numbers = listOf(numbers.trim().trimEnd(',', '،'), number).filter { it.isNotBlank() }.joinToString(", ")
    }
    val recipients = parseRecipients(numbers)
    fun start() {
        if (recipients.isNotEmpty()) model.startConversation(recipients)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = numbers,
            onValueChange = { numbers = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.new_conversation_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { start() }),
        )
        TextButton(onClick = { pickPhone.launch(Unit) }) { Text(stringResource(R.string.pick_contact)) }
        if (recipients.size > 1) {
            Text(stringResource(R.string.new_conversation_group_hint), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = ::start, enabled = recipients.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_conversation))
        }
    }
}

/** شماره‌ها جدا با ویرگول فارسی یا لاتین یا نقطه‌ویرگول؛ ارقام فارسی لاتین می‌شوند. */
private fun parseRecipients(text: String): List<String> =
    PersianText.latinDigits(text)
        .split(',', '،', ';', '؛', '\n')
        .map { it.trim() }
        .filter { it.any(Char::isLetterOrDigit) }

/** انتخابگر شمارهٔ تلفن اندروید؛ خروجی نشانی دادهٔ همان شماره است. */
private object PickPhone : ActivityResultContract<Unit, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): android.net.Uri? =
        intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}
