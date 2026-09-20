package ir.asudehapp.sms.telephony

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.persian.PersianText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * دکمه‌های اعلان (D41): «کپی رمز»، «پاسخ» و «خوانده شد». export نشده است؛
 * فقط PendingIntent خود اپ به آن می‌رسد.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        val folder = intent.getStringExtra(EXTRA_FOLDER)
            ?.let { runCatching { Folder.valueOf(it) }.getOrNull() }
            ?: Folder.INBOX
        val host = context.telephonyHost

        when (intent.action) {
            ACTION_COPY_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: return
                copyCode(context, code)
            }

            ACTION_MARK_READ -> launch {
                host.repository.markThreadRead(threadId, folder)
                host.notifier.cancelThread(threadId)
            }

            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY)
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val address = intent.getStringExtra(EXTRA_ADDRESS)?.takeIf { it.isNotBlank() } ?: return
                val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
                launch {
                    // اگر ثبت یا ارسال شکست بخورد، `SmsSender` خودش اعلان «ارسال نشد» می‌دهد.
                    runCatching { host.smsSender.send(address, text, subscriptionId) }
                        .onFailure { Log.e(TAG, "پاسخ از اعلان ثبت نشد", it) }
                    host.repository.markThreadRead(threadId, folder)
                    host.notifier.cancelThread(threadId)
                }
            }
        }
    }

    private fun BroadcastReceiver.launch(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } catch (failure: Throwable) {
                Log.e(TAG, "دکمهٔ اعلان انجام نشد", failure)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * رمز به‌عنوان دادهٔ حساس کپی می‌شود تا اندروید ۱۳ به بعد آن را در
     * پیش‌نمایش کلیپ‌بورد نشان ندهد. ارقامش همیشه لاتین کپی می‌شود، تا هر جا
     * چسبانده شود کار کند.
     */
    private fun copyCode(context: Context, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(
            context.getString(R.string.channel_otp),
            PersianText.latinDigits(code),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
        // از اندروید ۱۳، خود سیستم کپی شدن را نشان می‌دهد.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.code_copied, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_COPY_CODE = "ir.asudehapp.sms.COPY_CODE"
        const val ACTION_REPLY = "ir.asudehapp.sms.REPLY"
        const val ACTION_MARK_READ = "ir.asudehapp.sms.MARK_READ"

        const val EXTRA_NOTIFICATION_ID = "ir.asudehapp.sms.NOTIFICATION_ID"
        const val EXTRA_THREAD_ID = "ir.asudehapp.sms.THREAD_ID"
        const val EXTRA_FOLDER = "ir.asudehapp.sms.FOLDER"
        const val EXTRA_ADDRESS = "ir.asudehapp.sms.ADDRESS"
        const val EXTRA_SUBSCRIPTION_ID = "ir.asudehapp.sms.SUBSCRIPTION_ID"
        const val EXTRA_CODE = "ir.asudehapp.sms.CODE"
        const val KEY_REPLY = "ir.asudehapp.sms.REPLY_TEXT"

        /** `ClipDescription.EXTRA_IS_SENSITIVE` از API 33؛ رشته‌اش پیش از آن هم بی‌خطر است. */
        private val EXTRA_IS_SENSITIVE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }

        private const val TAG = "AsudehNotifyAction"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
